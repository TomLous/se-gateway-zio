/**
 * * WIP!!!!!
 */
object Gateway { /*extends ZIOAppDefault {

  val slf4j = SLF4J.slf4j(LogLevel.Debug, LogFormat.colored)

  override def hook: RuntimeConfigAspect = slf4j >>> RuntimeConfigAspect.enableCurrentFiber

  // Define env
  type AppEnv = AppConfig
//  private val loggingLayer = AppLogging.defautLayer

  private val offsetLayer = AppConfig.live.narrow(_.offset) >>> OffsetLive.layer // feed config into offset.live

  private val dnwgApiLayer =
    (AppConfig.live.narrow(_.dnwgApi)
      ++ HttpClientZioBackend.managed().toLayer) >>> DNWGApiLive.layer // feed config + http client into api.live
  type ApiEnv = Offset with DNWGApi

  private val kafkaProducerLayer = AppConfig.live.narrow(_.kafka) >>> KafkaProducerLive.layer
  type KafkaEnv = Kafka with TransactionalProducer with Clock

  // Get all metering point data
  val getMeteringPointData: ZIO[ApiEnv, Throwable, Iterable[KafkaMetaRecord[MeteringPointData]]] = for {
    _                 <- ZIO.logDebug("type=MeteringPointData action=start external-source=api")
    fromDate          <- Offset.getStartOffset
    toDate            <- Offset.getEndOffset(fromDate)
    meteringPointData <- DNWGApi.getAllMeteringPointData(fromDate, toDate)
    meteringPointRecords <-
      ZIO.succeed(
        meteringPointData.map(data => KafkaMetaRecord("dummy", data, key = Some(data.meteringPointID)))
      )
    _                 <- Offset.setNextOffset(toDate)
    _                 <- ZIO.logDebug(s"type=MeteringPointData action=received external-source=api num=${meteringPointData.size}")
  } yield meteringPointRecords

  // Get all metering points
  val getMeteringPoints: ZIO[ApiEnv, Throwable, Iterable[KafkaMetaRecord[MeteringPoint]]] = for {
    _              <- ZIO.logDebug("type=MeteringPoint action=start external-source=api ")
    meteringPoints <- DNWGApi.getMeteringPoints
    meteringPointRecords <-
      ZIO.succeed(
        meteringPoints.map(data => KafkaMetaRecord("dummy", data, key = Some(data.meteringPointID)))
      )
    _              <- ZIO.logDebug(s"type=MeteringPoint action=received external-source=api num=${meteringPoints.size}")
  } yield meteringPointRecords

  // send an iterable to kafka as transactional stream
  def sendToKafka[A <: Product: Manifest](
    data: Iterable[KafkaMetaRecord[A]]
  ): ZIO[AppEnv with KafkaEnv, Throwable, Unit] = {
    (for {
      config     <- ZIO.service[AppConfig]
      clock      <- ZIO.service[Clock]
      ingestedAt <- clock.instant
      _          <- ZIO.logDebug(s"type=${manifest[A].runtimeClass.getSimpleName} action=start external-source=kafka")
      _ <- ZStream
             .fromIterable(data)
             .mapZIO(item => Kafka.createRecord(item.data, item.headers, item.key)())
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
    meteringPoints    <- getMeteringPoints
    _                 <- sendToKafka(meteringPoints)
  } yield ()

  // run
  override def run: ZIO[Environment with ZEnv with ZIOAppArgs, Any, Any] =
    program
      .provideCustomLayer(AppConfig.live ++ offsetLayer ++ dnwgApiLayer ++ kafkaProducerLayer)
      .foldZIO(e => ZIO.logError(e.getMessage), s => ZIO.succeed(s))
 */
}
