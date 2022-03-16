import cdf.model.InternalSchemaRecord
import cdf.services.kafka._
import services._
import services.dnwg._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio._
import zio.config.syntax._
import zio.kafka.producer._
import zio.logging._
import zio.logging.backend.SLF4J
import zio.stream._

object ManualBackfill extends ZIOAppDefault {

  // For the logging => make json logging
  val slf4j: RuntimeConfigAspect         = SLF4J.slf4j(LogLevel.Debug, LogFormat.colored)
  override def hook: RuntimeConfigAspect = slf4j >>> RuntimeConfigAspect.enableCurrentFiber

  // ENV
  type AppEnv = AppConfig with ManualConfig

  private val dnwgApiLayer =
    (AppConfig.live.narrow(_.dnwgApi)
      ++ HttpClientZioBackend.managed().toLayer) >>> DNWGApiLive.layer // feed config + http client into api.live
  type ApiEnv = DNWGApi with ManualConfig

  private val kafkaProducerLayer = AppConfig.live.narrow(_.kafka) >>> KafkaProducerLive.layer
  type KafkaEnv = Kafka with TransactionalProducer with Clock

  // APP

  // Get  metering point data for id
  val getMeteringPointData: ZIO[ApiEnv, Throwable, Iterable[InternalSchemaRecord]] = for {
    config            <- ZIO.service[ManualConfig]
    _                 <- ZIO.logDebug(s"type=MeteringPoint action=request external-source=api id=${config.meteringPointId}")
    meteringPoint     <- DNWGApi.getMeteringPoint(config.meteringPointId)
    _                 <- ZIO.logDebug(s"type=MeteringPoint action=received external-source=api id=${config.meteringPointId}")
    _                 <- ZIO.logDebug(s"type=ChannelData action=request external-source=api metering-point-id=${config.meteringPointId} from=${config.offsetDate} to=${config.toDate.getOrElse("∞")}")
    meteringPointData <- DNWGApi.getMeteringPointData(config.meteringPointId, config.offsetDate)
    _                 <- ZIO.logDebug(s"type=ChannelData action=received external-source=api num=${meteringPointData.size}")
  } yield meteringPoint ++ meteringPointData.map(_.copy(_key))

  // send an iterable to kafka as transactional stream
  def sendToKafka[A <: InternalSchemaRecord: Manifest](
    data: Iterable[A]
  ): ZIO[AppEnv with KafkaEnv, Throwable, Unit] = {
    (for {
      config     <- ZIO.service[AppConfig]
      clock      <- ZIO.service[Clock]
      ingestedAt <- clock.instant
      _          <- ZIO.logDebug(s"type=${manifest[A].runtimeClass.getSimpleName} action=start external-source=kafka")
      _ <- ZStream
             .fromIterable(data)
             .mapZIO(item =>
               Kafka.createRecord(
                 item,
                 item._headers(config.name, ingestedAt),
                 item._key(con)
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
