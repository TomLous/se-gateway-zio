import cdf.model.KafkaMetaRecord
import cdf.services.kafka._
import services._
import services.dnwg._
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio._
import zio.config.syntax._
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
  type ApiEnv = DNWGApi with AppEnv

  private val kafkaProducerLayer = AppConfig.live.narrow(_.kafka) >>> KafkaProducer.live
  type KafkaEnv = KafkaProducer with Clock

  // APP

//  def wrapData[T](container: IterableOnce[Iterable[T]]): ZIO[ApiEnv, Throwable, Iterable[KafkaMetaRecord[T]]] = for{
//    appConfig     <- ZIO.service[AppConfig]
//    manualConfig  <- ZIO.service[ManualConfig]
//    result <-  ZIO.succeed(
//      container.iterator.map(data => KafkaMetaRecord(appConfig.name, data, key = Some(data.meteringPointID)))
//    )
//  } yield  result

  // Get  metering point data for id
  val getMeteringPointData: ZIO[ApiEnv, Throwable, Iterable[KafkaMetaRecord[_]]] = for {
    appConfig     <- ZIO.service[AppConfig]
    manualConfig  <- ZIO.service[ManualConfig]
    _             <- ZIO.logDebug(s"type=MeteringPoint action=request external-source=api id=${manualConfig.meteringPointId}")
    meteringPoint <- DNWGApi.getMeteringPoint(manualConfig.meteringPointId)
    meteringPointRecord <-
      ZIO.succeed(
        meteringPoint.map(data => KafkaMetaRecord(appConfig.name, data, key = Some(data.meteringPointID)))
      )
    _ <- ZIO.logDebug(s"type=MeteringPoint action=received external-source=api id=${manualConfig.meteringPointId}")
    _ <-
      ZIO.logDebug(
        s"type=ChannelData action=request external-source=api metering-point-id=${manualConfig.meteringPointId} from=${manualConfig.offsetDate} to=${manualConfig.toDate
          .getOrElse("∞")}"
      )
    meteringPointData <- DNWGApi.getMeteringPointData(manualConfig.meteringPointId, manualConfig.offsetDate)
    meteringPointDataRecords <-
      ZIO.succeed(
        meteringPointData.map(data => KafkaMetaRecord(appConfig.name, data, key = meteringPoint.map(_.meteringPointID)))
      )
    _ <- ZIO.logDebug(s"type=ChannelData action=received external-source=api num=${meteringPointData.size}")
  } yield meteringPointRecord ++ meteringPointDataRecords

  // send an iterable to kafka as transactional stream
  def sendToKafka[A: Manifest](
    data: Iterable[KafkaMetaRecord[A]]
  ): ZIO[AppEnv with KafkaEnv, Throwable, Unit] = {
    (for {
      config     <- ZIO.service[AppConfig]
      clock      <- ZIO.service[Clock]
      ingestedAt <- clock.instant
      _          <- ZIO.logDebug(s"type=${manifest[A].getClass.getSimpleName} action=start external-source=kafka")
      _ <- ZStream
             .fromIterable(data)
             .mapZIO(item =>
               KafkaProducer(
                 _.createRecord(
                   item.data,
                   item.headers,
                   item.key
                 )
               )
             )
             .mapChunksZIO(chunk => KafkaProducer(_.produceRecordChunk(chunk)))
             .runDrain
      _ <- ZIO.logDebug(s"type=${manifest[A].runtimeClass.getSimpleName} action=stop external-source=kafka")
    } yield ())
//      .provideSomeLayer[AppEnv with KafkaEnv]( // assume AppEnv & KafkaEnv are provided, just add the Transaction Layer
//        Kafka.createTransactionLayer
//      )
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
//      .foldZIO(e => ZIO.logError(e.getMessage), s => ZIO.succeed(s))

}
