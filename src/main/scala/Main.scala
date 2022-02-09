import model.DNWGResponse.{MeteringPoint, MeteringPointData}
import service._
import sttp.client3.httpclient.zio._
import zio._
import zio.blocking.Blocking
import zio.config.syntax._
import zio.kafka.producer.TransactionalProducer
import zio.logging._
import zio.stream.ZStream

import scala.language.postfixOps

object Main extends App {

  // Define env
  private val loggingLayer = AppLogging.defautLayer

  private val offsetConfig = AppConfig.live.narrow(_.offset)
  private val offsetLayer  = offsetConfig >>> Offset.live // feed config into offset.live

  private val dnwgApiConfig = AppConfig.live.narrow(_.dnwgApi)
  private val dnwgApiLayer  = (dnwgApiConfig ++ HttpClientZioBackend.managed().toLayer) >>> DNWGApi.live // feed config + http client into api.live

  private val kafkaProducerConfig = AppConfig.live.narrow(_.kafka)
  private val kafkaProducerLayer  = (kafkaProducerConfig ++ Blocking.live) >>> Kafka.live

  // Get all metering point data
  val getMeteringPointData: ZIO[Has[Offset.Service] with Has[DNWGApi.Service] with Logging, Throwable, Iterable[MeteringPointData]] = for {
    _                 <- log.debug("Getting MeteringPointData")
    fromDate          <- Offset.getStartOffset
    toDate            <- Offset.getEndOffset(fromDate)
    meteringPointData <- DNWGApi.getAllMeteringPointData(fromDate, toDate)
    _                 <- Offset.setNextOffset(toDate)
    _                 <- log.debug("Data: " + meteringPointData.size)
  } yield meteringPointData

  // convert metering point data to a stream
  val getMeteringPointDataStream: ZStream[Has[Offset.Service] with Has[DNWGApi.Service] with Logging, Throwable, MeteringPointData] =
    ZStream.fromIteratorEffect(getMeteringPointData.map(_.iterator))

  // Get all metering points
  val getMeteringPoints: ZIO[Has[DNWGApi.Service] with Logging, Throwable, Iterable[MeteringPoint]] = for {
    _              <- log.debug("Getting MeteringPoints")
    meteringPoints <- DNWGApi.getMeteringPoints
    _              <- log.debug("Points: " + meteringPoints.size)
  } yield meteringPoints

  // convert metering points to a stream
//  val getMeteringPointsStream: ZStream[Has[DNWGApi.Service] with Logging, Throwable, MeteringPoint] =
//    ZStream.fromIteratorEffect(getMeteringPoints.map(_.iterator))

//  def sendMessages[T](
//    stream: ZStream[Has[Kafka.Service] with Has[TransactionalProducer] with Logging, Throwable, T]
//  ): ZManaged[Has[Kafka.Service] with Has[Transaction] with Logging, Throwable, Unit] = for {
//    _ <- TransactionalProducer.createTransaction
//
//    _ <- stream
//           .mapM(item => Kafka.createRecord(item))
//           .mapChunksM(Kafka.produceRecordChunk)
//           .runDrain
//           .toManaged_
//  } yield ()

  // actual program
  val program: ZIO[Has[Kafka.Service] with Has[TransactionalProducer] with Has[Offset.Service] with Has[DNWGApi.Service] with Logging, Throwable, Unit] = {

    ( ZStream
      .fromIteratorEffect(getMeteringPointData.map(_.iterator))
              .mapM(item => Kafka.createRecord(item))
              .mapChunksM(Kafka.produceRecordChunk)
              .runDrain)
      .provideSomeLayer(Kafka.createTransactionLayer)


  }

  // run
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    program
      .foldM(
        error =>
          log
            .error("Error: " + error + error.printStackTrace())
            .as(ExitCode(1)),
        _ =>
          log
            .info("Success")
            .as(ExitCode(0))
      )
      .provideCustomLayer(loggingLayer ++ offsetLayer ++ dnwgApiLayer ++ kafkaProducerLayer)
      .exitCode
  }

//  private val dnwgHeaders = Map(
//    "ce_specversion" -> "1.0",
//    "ce_type"        -> "nl.schiphol.dna.cdf.model.DNWGMeterPointRaw",
//    "ce_source"      -> sourceName,
//    "ce_id"          -> "1234"
//  )
//  private val kafkaProducerSettings = ProducerSettings(List("localhost:29092", "localhost:29093", "localhost:29094"))
//  private val kafkaTransactionalProducerSettings =
//    TransactionalProducerSettings(kafkaProducerSettings, "dnwg-all-transaction")
//  private val kafkaTopic = "test-topic"
//
//  // to add some meta data for headers ect to the json blobs
//  case class MeteringData(json: String, batch: String, index: Long)
//
//  // for parsing raw json
//  implicit val format = org.json4s.DefaultFormats
//
//  // config the env for this app
//  private val loggingLayer = Logging.console(
//    logLevel = logLevel,
//    format = LogFormat.ColoredLogFormat()
//  ) >>> Logging.withRootLoggerName(sourceName)
//
//  private val allLayers =
//    loggingLayer ++
//      HttpClientZioBackend.layer() ++
//      ZLayer.fromManaged(TransactionalProducer.make(kafkaTransactionalProducerSettings))
//
//  // FUNCTIONS
//
//  // gets the last offset
//  val getOffset: LocalDateTime = LocalDateTime.parse("2022-01-18T22:00:00")
//
//
//  val getMeteringPointsRequest:  Request[Either[String, String], Any] =
//        basicRequest.auth
//          .bearer(dnwgApiToken)
//          .readTimeout(dnwgApiReadTimeout.asScala)
//          .get(
//            uri"""$dnwgHost/api/v1/meteringPoints"""
//          )
//
//  // does the http api call
//  val getAllRequest: LocalDateTime => LocalDateTime => Request[Either[String, String], Any] =
//    fromIsoDate =>
//      toIsoDate =>
//        basicRequest.auth
//          .bearer(dnwgApiToken)
//          .readTimeout(dnwgApiReadTimeout.asScala)
//          .get(
//            uri"""$dnwgHost/api/v1/meteringPoints/all/meteringdata/interval?periodStartdate=$fromIsoDate&periodEnddate=$toIsoDate&extendedData=registerreading"""
//          )
//
//  // extract the raw json items and wrap them in a nice metadata object
//  val extractMeteringPoints: String => JValue => List[MeteringData] =
//    batchName =>
//      json =>
//        (json \\ "items")
//          .extract[List[JObject]]
//          .map(jval => compact(render(jval)))
//          .zipWithIndex
//          .map { case (json, idx) =>
//            MeteringData(json, batchName, idx)
//          }
//
//  val getRecordHeaders: MeteringData => java.lang.Iterable[Header] = meteringData =>
//    (dnwgHeaders ++ Map(
//      "batch" -> meteringData.batch,
//      "index" -> meteringData.index.toString
//    )).map { case (k, v) =>
//      new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8)).asInstanceOf[Header]
//    }.asJava
//
//  // generate the kafka message
//  val createRecord: MeteringData => ProducerRecord[String, String] = meteringData =>
//    new ProducerRecord[String, String](kafkaTopic, null, null, null, meteringData.json, getRecordHeaders(meteringData))
//
//  // produce a bunch of records per chunk on within a transaction
//  val produceRecords: Transaction => Chunk[ProducerRecord[String, String]] => RIO[ZEnv with Has[TransactionalProducer], Chunk[RecordMetadata]] =
//    transaction => chunk => transaction.produceChunk(chunk, Serde.string, Serde.string, None)
//
//  // BETTER ERRORS
//
//  // Tries to get the json error message, otherwise just the original message
//  val apiErrorJson: String => Exception = json => new Exception(Try((parse(json) \\ "error").extract[String]).getOrElse(json))
//
//  // Better verbosity
//  val wrapException: String => Throwable => Throwable = message => ex => new Exception(s"$message: ${ex.getMessage}", ex)
//
//  // This will return the meteringpoints as a list of points as json strings from the endpoint. No type checking at t
//  val getMeteringPoints: ZIO[SttpClient with Logging, Throwable, List[MeteringData]] = for {
//    fromDateTime <- ZIO.succeed(getOffset)
//    toDateTime   <- ZIO.succeed(fromDateTime.plusHours(1))
//    _            <- log.debug(s"From date $fromDateTime - $toDateTime")
//    response     <- send(getAllRequest(fromDateTime)(toDateTime)).mapError(wrapException("API call failed"))
//    response2     <- send(getMeteringPointsRequest).mapError(wrapException("API call failed"))
//    body2        <- ZIO.fromEither(response2.body).mapError(apiErrorJson)
//    _             <- log.debug(body2.take(1000))
//    body         <- ZIO.fromEither(response.body).mapError(apiErrorJson)
//    _             <- log.debug(body.take(1000))
//    _            <- log.debug(s"Received ${body.length} bytes")
//    json         <- ZIO(parse(body)).mapError(wrapException("Parsing json body failed"))
//    meteringPoints <- ZIO(extractMeteringPoints(s"$fromDateTime/$toDateTime")(json))
//                        .mapError(wrapException("Extracting json body failed"))
//    _ <- log.debug(s"Received ${meteringPoints.size} items from endpoint")
//  } yield meteringPoints



//
//  val program = for {
//    meteringPoints <- getMeteringPoints
//    _ <- TransactionalProducer.createTransaction.use { transaction =>
//           ZStream
//             .fromIterable(meteringPoints)
//             .map(createRecord)
//             .mapChunksM(produceRecords(transaction))
//             .runDrain
//         }
//    _ <-
//      log.debug(
//        s"Wrote ${meteringPoints.size} to kafka topic ${kafkaTopic} using batch ${meteringPoints.headOption.map(_.batch).getOrElse("N/A")}"
//      )
//  } yield ()
//
//  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
//    program
//      .foldM(
//        error =>
//          log
//            .error("Error: " + error.getMessage)
//            .as(ExitCode(1)),
//        _ =>
//          log
//            .info("Success")
//            .as(ExitCode(0))
//      )
//      .provideCustomLayer(allLayers)
//      .retryN(3)
//      .orDie
//  }
}
