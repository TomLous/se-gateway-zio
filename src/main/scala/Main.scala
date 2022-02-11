import model.InternalSchemaRecord
import model.smartenergy.DNWGResponse.{MeteringPoint, MeteringPointData}
import service._
import sttp.client3.httpclient.zio._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.config.syntax._
import zio.kafka.producer.TransactionalProducer
import zio.logging._
import zio.stream.ZStream

import scala.language.postfixOps

object Main extends App {

  // Define env
  type AppEnv = Has[AppConfig]
  private val loggingLayer = AppLogging.defautLayer

  private val offsetConfig = AppConfig.live.narrow(_.offset)
  private val offsetLayer  = offsetConfig >>> Offset.live // feed config into offset.live

  private val dnwgApiConfig = AppConfig.live.narrow(_.dnwgApi)
  private val dnwgApiLayer  = (dnwgApiConfig ++ HttpClientZioBackend.managed().toLayer) >>> DNWGApi.live // feed config + http client into api.live
  type ApiEnv = Has[Offset.Service] with Has[DNWGApi.Service] with Logging

  private val kafkaProducerConfig = AppConfig.live.narrow(_.kafka)
  private val kafkaProducerLayer  = (kafkaProducerConfig ++ Blocking.live) >>> Kafka.live
  type KafkaEnv = Has[Kafka.Service] with Has[TransactionalProducer] with Logging with Clock

  // Get all metering point data
  val getMeteringPointData: ZIO[ApiEnv, Throwable, Iterable[MeteringPointData]] = for {
    _                 <- log.debug("type=MeteringPointData action=start external-source=api")
    fromDate          <- Offset.getStartOffset
    toDate            <- Offset.getEndOffset(fromDate)
    meteringPointData <- DNWGApi.getAllMeteringPointData(fromDate, toDate)
    _                 <- Offset.setNextOffset(toDate)
    _                 <- log.debug(s"type=MeteringPointData action=received external-source=api num=${meteringPointData.size}")
  } yield meteringPointData

  // Get all metering points
  val getMeteringPoints: ZIO[ApiEnv, Throwable, Iterable[MeteringPoint]] = for {
    _              <- log.debug("type=MeteringPoint action=start external-source=api ")
    meteringPoints <- DNWGApi.getMeteringPoints
    _              <- log.debug(s"type=MeteringPoint action=received external-source=api num=${meteringPoints.size}")
  } yield meteringPoints

  // send an iterable to kafka as transactional stream
  def sendToKafka[A <: InternalSchemaRecord: Manifest](
    data: Iterable[A]
  ): ZIO[AppEnv with KafkaEnv, Throwable, Unit] = {
    (for {
      config     <- ZIO.service[AppConfig]
      clock      <- ZIO.service[Clock.Service]
      ingestedAt <- clock.instant
      _          <- log.debug(s"type=${manifest[A].runtimeClass.getSimpleName} action=start external-source=kafka")
      _ <- ZStream
             .fromIterable(data)
             .mapM(item => Kafka.createRecord(item, item._headers(config.name, ingestedAt), item._key)())
             .mapChunksM(Kafka.produceRecordChunk)
             .runDrain
      _ <- log.debug(s"type=${manifest[A].runtimeClass.getSimpleName} action=stop external-source=kafka")
    } yield ())
      .provideSomeLayer[AppEnv with KafkaEnv]( // assume AppEnv & KafkaEnv are provided, just add the Transaction Layer
        Kafka.createTransactionLayer
      )
  }

  // compose the program to be run
  val program: ZIO[AppEnv with KafkaEnv with ApiEnv, Throwable, Unit] = for {
    meteringPointData <- getMeteringPointData
    _                 <- sendToKafka(meteringPointData)
    meteringPoints    <- getMeteringPoints
    _                 <- sendToKafka(meteringPoints)
  } yield ()

  // run
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    program
      .foldM(
        error =>
          log
            .error(s"error='$error'" + error.printStackTrace())
            .as(ExitCode(1)),
        _ =>
          log
            .info("done")
            .as(ExitCode(0))
      )
      .provideCustomLayer(AppConfig.live ++ loggingLayer ++ offsetLayer ++ dnwgApiLayer ++ kafkaProducerLayer)
      .exitCode
  }

}
