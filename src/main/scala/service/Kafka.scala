package service

import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import zio._
import zio.blocking.Blocking
import zio.kafka.producer.{ProducerSettings, Transaction, TransactionalProducer, TransactionalProducerSettings}
import zio.kafka.serde.Serde

import java.lang
import java.nio.charset.StandardCharsets
import java.time.{LocalDateTime, ZoneOffset}
import scala.jdk.CollectionConverters._

object Kafka {

  // This is the service definition. All Services (live, mock, etc) need to implement these methods
  trait Service {
    def createRecord[T](
      item: T,
      headers: Map[String, String] = Map.empty,
      key: Option[String] = None,
      partition: Option[Int] = None,
      timestamp: Option[LocalDateTime] = None
    ): ZIO[Any, Throwable, ProducerRecord[String, String]]

    def produceRecordChunk(chunk: Chunk[ProducerRecord[String, String]]): ZIO[Has[Transaction], Throwable, Chunk[RecordMetadata]]
  }

  def createRecord[T](
    item: T,
    headers: Map[String, String] = Map.empty,
    key: Option[String] = None,
    partition: Option[Int] = None,
    timestamp: Option[LocalDateTime] = None
  ): ZIO[Has[Kafka.Service], Throwable, ProducerRecord[String, String]] =
    ZIO.accessM(_.get.createRecord(item, headers, key, partition, timestamp))

  def produceRecordChunk(
    chunk: Chunk[ProducerRecord[String, String]]
  ): ZIO[Has[Kafka.Service] with Has[Transaction], Throwable, Chunk[RecordMetadata]] =
    ZIO.accessM(_.get.produceRecordChunk(chunk))

  // Config for the API
  case class Config(topic: String, brokers: List[String], transactionId: String, defaultHeaders: Map[String, String] = Map.empty) {
    lazy val producerSettings: ProducerSettings                           = ProducerSettings(brokers)
    lazy val transactionalProducerSettings: TransactionalProducerSettings = TransactionalProducerSettings(producerSettings, transactionId)
  }

  val transactionalProducerLive:ZLayer[Blocking with Has[Config], Throwable, Has[TransactionalProducer]] =  ZLayer.fromManaged {
    for {
      kafkaConfig           <- ZIO.service[Kafka.Config].toManaged_
      transactionalProducer <- TransactionalProducer.make(kafkaConfig.transactionalProducerSettings)
    } yield transactionalProducer
  }

  val live: ZLayer[Has[Kafka.Config] with Blocking, Throwable, Has[Kafka.Service] with Has[TransactionalProducer]] = ZLayer.fromService[Kafka.Config, Kafka.Service] {
    KafkaProducerServiceLive
  } ++ transactionalProducerLive

  def createTransactionLayer: ZLayer[Has[TransactionalProducer], Throwable, Has[Transaction]] =
    for {
      transactionalProducer <- ZLayer.service[TransactionalProducer]
      transaction <- transactionalProducer.get.createTransaction.toLayer
    } yield transaction


  case class KafkaProducerServiceLive(config: Kafka.Config) extends Kafka.Service {

    private def getRecordHeaders(headers: Map[String, String]): Task[lang.Iterable[Header]] = ZIO(headers.map { case (k, v) =>
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

    private def getJson[T](item: T): Task[java.lang.String] = {
      ???
    }

    override def createRecord[T](
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
        json          <- getJson(item)
        record <- ZIO(
                    new ProducerRecord[String, String](config.topic, partitionSafe.orNull, timestampSafe.orNull, keySafe.orNull, json, recordHeaders)
                  )
      } yield record

    }

    override def produceRecordChunk(chunk: Chunk[ProducerRecord[String, String]]): ZIO[Has[Transaction], Throwable, Chunk[RecordMetadata]] =
      for {
        transaction <- ZIO.service[Transaction]
        metadata    <- transaction.produceChunk(chunk, Serde.string, Serde.string, None)
      } yield metadata
  }
}