import cdf.model.InternalSchemaRecord
import cdf.services.kafka._
import services._
import services.dnwg._
import smartenergy.DNWGResponse._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio._
import zio.config.syntax._
import zio.kafka.producer._
import zio.logging._
import zio.logging.backend.SLF4J
import zio.stream._

object ManualBackfill extends ZIOAppDefault {

  val slf4j: RuntimeConfigAspect         = SLF4J.slf4j(LogLevel.Debug, LogFormat.colored)
  override def hook: RuntimeConfigAspect = slf4j >>> RuntimeConfigAspect.enableCurrentFiber

  type AppEnv = AppConfig with ManualConfig

  private val dnwgApiLayer =
    (AppConfig.live.narrow(_.dnwgApi)
      ++ HttpClientZioBackend.managed().toLayer) >>> DNWGApiLive.layer // feed config + http client into api.live
  type ApiEnv = DNWGApi with ManualConfig

  private val kafkaProducerLayer = AppConfig.live.narrow(_.kafka) >>> KafkaProducerLive.layer
  type KafkaEnv = Kafka with TransactionalProducer with Clock

  // Get  metering point data for id
  val getMeteringPointData: ZIO[ApiEnv, Throwable, Iterable[ChannelData]] = for {
    _      <- ZIO.logDebug("type=MeteringPointData action=start external-source=api")
    config <- ZIO.service[ManualConfig]
    meteringPoint <- DNWGApi.getMeteringPoints
                       .map(_.find(meteringPoint => meteringPoint.meteringPointID == config.meteringPointId))
    _                 <- ZIO.logDebug(meteringPoint.toString)
    meteringPointData <- DNWGApi.getMeteringPointData(config.meteringPointId, config.offsetDate)
    _                 <- ZIO.logDebug(s"type=MeteringPointData action=received external-source=api num=${meteringPointData.size}")
  } yield meteringPointData

  // send an iterable to kafka as transactional stream
  def sendToKafka[A <: InternalSchemaRecord: Manifest](
    data: Iterable[A]
  ): ZIO[AppEnv with KafkaEnv, Throwable, Unit] = {
    (for {
      config       <- ZIO.service[AppConfig]
      manualConfig <- ZIO.service[ManualConfig]
      clock        <- ZIO.service[Clock]
      ingestedAt   <- clock.instant
      _            <- ZIO.logDebug(s"type=${manifest[A].runtimeClass.getSimpleName} action=start external-source=kafka")
      _ <- ZStream
             .fromIterable(data)
             .mapZIO(item =>
               Kafka.createRecord(
                 item,
                 item._headers(config.name, ingestedAt),
                 Some(s"${manualConfig.meteringPointId}-${item._key.getOrElse("?")}")
               )()
             )
             .mapChunksZIO(Kafka.produceRecordChunk)
             .runDrain
      _ <- ZIO.logDebug(s"type=${manifest[A].runtimeClass.getSimpleName} action=stop external-source=kafka")
    } yield ())
      .provideSomeLayer[AppEnv with KafkaEnv]( // assume AppEnv & KafkaEnv are provided, just add the Transaction Layer
        Kafka.createTransactionLayer
      )
  }

  // compose the program to be run
  val program: ZIO[AppEnv with KafkaEnv with ApiEnv, Throwable, Unit] = for {
    meteringPointData <- getMeteringPointData
    _                 <- sendToKafka(meteringPointData)
  } yield ()

  // run
  override def run: ZIO[Environment with ZEnv with ZIOAppArgs, Any, Any] =
    program
      .provideCustomLayer(AppConfig.live ++ ManualConfig.live ++ dnwgApiLayer ++ kafkaProducerLayer)
      .foldZIO(e => ZIO.logError(e.getMessage), s => ZIO.succeed(s))

}
