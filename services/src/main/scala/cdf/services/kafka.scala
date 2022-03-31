package cdf.services

import cdf.util.Json
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import zio._
import zio.kafka.producer.{Producer, _}
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
  trait KafkaProducer {
    def createRecord[T: Manifest](
      item: T,
      headers: Map[String, String] = Map.empty,
      key: Option[String] = None,
      partition: Option[Int] = None,
      timestamp: Option[LocalDateTime] = None
    ): Task[ProducerRecord[String, String]]

    def produceTransactionalRecordChunk(
      chunk: Chunk[ProducerRecord[String, String]]
    ): RIO[Transaction, Chunk[RecordMetadata]]

    def produceRecordChunk(chunk: Chunk[ProducerRecord[String, String]]): Task[Chunk[RecordMetadata]]
  }

  // accessors
  object KafkaProducer extends Accessible[KafkaProducer] {
//      def createRecord[T: Manifest](item: T, headers: Map[String, String] = Map.empty, key: Option[String] = None)(
//      partition: Option[Int] = None,
//      timestamp: Option[LocalDateTime] = None
//    ): ZIO[Kafka, Throwable, ProducerRecord[String, String]] =
//      ZIO.serviceWithZIO(_.createRecord(item, headers, key, partition, timestamp))
//
//    def produceTransactionalRecordChunk(
//      chunk: Chunk[ProducerRecord[String, String]]
//    ): ZIO[Kafka with Transaction, Throwable, Chunk[RecordMetadata]] =
//      ZIO.serviceWithZIO[Kafka](_.produceTransactionalRecordChunk(chunk))
//
//    def produceRecordChunk(
//                            chunk: Chunk[ProducerRecord[String, String]]
//                          ): ZIO[Kafka, Throwable, Chunk[RecordMetadata]] =
//      ZIO.serviceWithZIO(_.produceRecordChunk(chunk))

//    private val transactionalProducerLive: ZLayer[Config, Throwable, TransactionalProducer] =
//      ZLayer.fromManaged {
//        for {
//          kafkaConfig           <- ZIO.service[Config].toManaged
//          transactionalProducer <- TransactionalProducer.make(kafkaConfig.transactionalProducerSettings)
//        } yield transactionalProducer
//      }

//    private val producerLive: ZLayer[Config, Throwable, Producer] =
//      ZLayer.fromManaged {
//        for {
//          kafkaConfig <- ZIO.service[Config].toManaged
//          producer    <- Producer.make(kafkaConfig.producerSettings)
//        } yield producer
//      }

//    val transactionalLayer: ZLayer[Config, Throwable, KafkaProducer with TransactionalProducer] =
//      (KafkaProducerLive(_)).toLayer[KafkaProducer] ++ transactionalProducerLive

    val live: ZLayer[Config, Throwable, KafkaProducer] =
      (KafkaProducerLive(_)).toLayer

    def createTransactionLayer: ZLayer[TransactionalProducer, Throwable, Transaction] =
      for {
        transactionalProducer <- ZLayer.service[TransactionalProducer]
        transaction           <- transactionalProducer.get.createTransaction.toLayer
      } yield transaction

  }

  // Config for Kafka
  case class Config(
    topic: String,
    brokers: List[String],
    transactionId: String,
    apiKey: Option[String],
    apiSecret: Option[String],
    defaultHeaders: Map[String, String] = Map.empty
  ) {
    private val sasl = (apiKey, apiSecret) match {
      case (Some(key), Some(secret)) =>
        Map(
          "security.protocol" -> "SASL_SSL",
          "sasl.mechanism"    -> "PLAIN",
          "sasl.jaas.config"  -> s"org.apache.kafka.common.security.plain.PlainLoginModule required username='$key' password='$secret';"
        )
      case _ => Map.empty[String, String]
    }

    private val defaultProps = Map(
      "client.id"                             -> "smartenergy-manual",
      "session.timeout.ms"                    -> "45000",
      "acks"                                  -> "all",
      "client.dns.lookup"                     -> "use_all_dns_ips",
      "max.in.flight.requests.per.connection" -> "1"
    )

    lazy val producerSettings: ProducerSettings =
      ProducerSettings(brokers)
        .withProperties(defaultProps ++ sasl)

    lazy val transactionalProducerSettings: TransactionalProducerSettings =
      TransactionalProducerSettings(producerSettings, transactionId)
  }

//  val transactionalProducerLive: ZLayer[Config, Throwable, TransactionalProducer] =
//    ZLayer.fromManaged {
//      for {
//        kafkaConfig           <- ZIO.service[Config].toManaged
//        transactionalProducer <- TransactionalProducer.make(kafkaConfig.transactionalProducerSettings)
//      } yield transactionalProducer
//    }

//  object KafkaProducerLive {
//    val transactionalLayer: ZLayer[Config, Throwable, Kafka with TransactionalProducer] =
//      (KafkaProducerLive(_)).toLayer[Kafka] ++ transactionalProducerLive
//
//    val layer: ZLayer[Config, Throwable, Kafka] =
//      (KafkaProducerLive(_)).toLayer[Kafka]
//  }

  case class KafkaProducerLive(config: Config) extends KafkaProducer {

    lazy private val producerLayer: ZLayer[Any, Throwable, Producer] =
      ZLayer.fromManaged(Producer.make(config.producerSettings))

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

    override def produceTransactionalRecordChunk(
      chunk: Chunk[ProducerRecord[String, String]]
    ): ZIO[Transaction, Throwable, Chunk[RecordMetadata]] =
      for {
        transaction <- ZIO.service[Transaction]
        metadata    <- transaction.produceChunk(chunk, Serde.string, Serde.string, None)
      } yield metadata

    override def produceRecordChunk(
      chunk: Chunk[ProducerRecord[String, String]]
    ): Task[Chunk[RecordMetadata]] =
      Producer.produceChunk(chunk, Serde.string, Serde.string).provideSomeLayer(producerLayer)
  }
}
