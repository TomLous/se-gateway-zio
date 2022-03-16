package cdf.services

import cdf.util.Json
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import zio._
import zio.kafka.producer._
import zio.kafka.serde.Serde

import java.lang
import java.nio.charset.StandardCharsets
import java.time.{LocalDateTime, ZoneOffset}
import scala.jdk.CollectionConverters._

object kafka {

  case class Record[T: Manifest](
    item: T,
    headers: Map[String, String] = Map.empty,
    key: Option[String] = None,
    partition: Option[Int] = None,
    timestamp: Option[LocalDateTime] = None
  )

  // This is the service definition. All Services (live, mock, etc) need to implement these methods
  trait Kafka {
    def createRecord[T: Manifest](
      item: T,
      headers: Map[String, String] = Map.empty,
      key: Option[String] = None,
      partition: Option[Int] = None,
      timestamp: Option[LocalDateTime] = None
    ): ZIO[Any, Throwable, ProducerRecord[String, String]]

    def produceRecordChunk(
      chunk: Chunk[ProducerRecord[String, String]]
    ): ZIO[Transaction, Throwable, Chunk[RecordMetadata]]
  }

  // accessors
  object Kafka {
    def createRecord[T: Manifest](item: T, headers: Map[String, String] = Map.empty, key: Option[String] = None)(
      partition: Option[Int] = None,
      timestamp: Option[LocalDateTime] = None
    ): ZIO[Kafka, Throwable, ProducerRecord[String, String]] =
      ZIO.serviceWithZIO(_.createRecord(item, headers, key, partition, timestamp))

    def produceRecordChunk(
      chunk: Chunk[ProducerRecord[String, String]]
    ): ZIO[Kafka with Transaction, Throwable, Chunk[RecordMetadata]] =
      ZIO.serviceWithZIO[Kafka](_.produceRecordChunk(chunk))

    def createTransactionLayer: ZLayer[TransactionalProducer, Throwable, Transaction] =
      for {
        transactionalProducer <- ZLayer.service[TransactionalProducer]
        transaction           <- transactionalProducer.get.createTransaction.toLayer
      } yield transaction

  }

  // Config for the API
  case class Config(
    topic: String,
    brokers: List[String],
    transactionId: String,
    defaultHeaders: Map[String, String] = Map.empty
  ) {
    lazy val producerSettings: ProducerSettings = ProducerSettings(brokers)
    lazy val transactionalProducerSettings: TransactionalProducerSettings =
      TransactionalProducerSettings(producerSettings, transactionId)
  }

  val transactionalProducerLive: ZLayer[Config, Throwable, TransactionalProducer] =
    ZLayer.fromManaged {
      for {
        kafkaConfig           <- ZIO.service[Config].toManaged
        transactionalProducer <- TransactionalProducer.make(kafkaConfig.transactionalProducerSettings)
      } yield transactionalProducer
    }


  object KafkaProducerLive {
    val layer: ZLayer[Config, Throwable, Kafka with TransactionalProducer] =
      (KafkaProducerLive(_)).toLayer[Kafka] ++ transactionalProducerLive
  }

  case class KafkaProducerLive(config: Config) extends Kafka {

    private def getRecordHeaders(headers: Map[String, String]): Task[lang.Iterable[Header]] = ZIO(headers.map {
      case (k, v) =>
        new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8)).asInstanceOf[Header]
    }.asJava)

    //noinspection WrapInsteadOfLiftInspection
    private def getUnixTime(timestamp: Option[LocalDateTime]): Task[Option[java.lang.Long]] =
      ZIO(
        timestamp
          .map(_.toEpochSecond(ZoneOffset.UTC))
          .map(l => l: java.lang.Long)
      )

    //noinspection WrapInsteadOfLiftInspection
    private def getPartition(partition: Option[Int]): Task[Option[java.lang.Integer]] = ZIO(
      partition
        .map(l => l: java.lang.Integer)
    )

    //noinspection WrapInsteadOfLiftInspection
    private def getKey(key: Option[String]): Task[Option[lang.String]] = ZIO(
      key
        .map(l => l: java.lang.String)
    )

    override def createRecord[T: Manifest](
      item: T,
      headers: Map[String, String] = Map.empty,
      key: Option[String] = None,
      partition: Option[Int] = None,
      timestamp: Option[LocalDateTime] = None
    ): ZIO[Any, Throwable, ProducerRecord[String, String]] = {

      for {
        recordHeaders <- getRecordHeaders(headers)
        partitionSafe <- getPartition(partition)
        timestampSafe <- getUnixTime(timestamp)
        keySafe       <- getKey(key)
        json          <- Json.renderJson(item)
        record <- ZIO(
                    new ProducerRecord[String, String](
                      config.topic,
                      partitionSafe.orNull,
                      timestampSafe.orNull,
                      keySafe.orNull,
                      json,
                      recordHeaders
                    )
                  )
      } yield record

    }

    override def produceRecordChunk(
      chunk: Chunk[ProducerRecord[String, String]]
    ): ZIO[Transaction, Throwable, Chunk[RecordMetadata]] =
      for {
        transaction <- ZIO.service[Transaction]
        metadata    <- transaction.produceChunk(chunk, Serde.string, Serde.string, None)
      } yield metadata
  }
}
