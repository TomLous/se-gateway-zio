import org.apache.kafka.clients.producer._
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import org.json4s.JsonAST.JObject
import org.json4s._
import org.json4s.native.JsonMethods._
import sttp.client3._
import sttp.client3.httpclient.zio._
import zio._
import zio.duration._
import zio.kafka.producer._
import zio.kafka.serde._
import zio.logging._
import zio.stream.ZStream

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.Try

object Main extends App {

  // put these in config

  private val sourceName         = "DNWG/all"
  private val logLevel           = LogLevel.Debug
  private val dnwgHost           = "https://emi.dnwg.nl"
  private val dnwgApiToken       = "b043adab-a6fb-491e-ba45-b2fa54c30409"
  private val dnwgApiReadTimeout = 1 minutes
  private val dnwgHeaders = Map(
    "ce_specversion" -> "1.0",
    "ce_type"        -> "nl.schiphol.dna.cdf.model.DNWGMeterPointRaw",
    "ce_source"      -> sourceName,
    "ce_id"          -> "1234"
  )
  private val kafkaProducerSettings = ProducerSettings(List("localhost:29092", "localhost:29093", "localhost:29094"))
  private val kafkaTransactionalProducerSettings =
    TransactionalProducerSettings(kafkaProducerSettings, "dnwg-all-transaction")
  private val kafkaTopic = "test-topic"

  // to add some meta data for headers ect to the json blobs
  case class MeteringData(json: String, batch: String, index: Long)

  // for parsing raw json
  implicit val format = org.json4s.DefaultFormats

  // config the env for this app
  private val loggingLayer = Logging.console(
    logLevel = logLevel,
    format = LogFormat.ColoredLogFormat()
  ) >>> Logging.withRootLoggerName(sourceName)

  private val allLayers =
    loggingLayer ++
      HttpClientZioBackend.layer() ++
      ZLayer.fromManaged(TransactionalProducer.make(kafkaTransactionalProducerSettings))

  // FUNCTIONS

  // gets the last offset
  val getOffset: LocalDateTime = LocalDateTime.parse("2022-01-18T22:00:00")

  // does the http api call
  val getAllRequest: LocalDateTime => LocalDateTime => Request[Either[String, String], Any] =
    fromIsoDate =>
      toIsoDate =>
        basicRequest.auth
          .bearer(dnwgApiToken)
          .readTimeout(dnwgApiReadTimeout.asScala)
          .get(
            uri"""$dnwgHost/api/v1/meteringPoints/all/meteringdata/interval?periodStartdate=$fromIsoDate&periodEnddate=$toIsoDate&extendedData=registerreading"""
          )

  // extract the raw json items and wrap them in a nice metadata object
  val extractMeteringPoints: String => JValue => List[MeteringData] =
    batchName =>
      json =>
        (json \\ "items")
          .extract[List[JObject]]
          .map(jval => compact(render(jval)))
          .zipWithIndex
          .map { case (json, idx) =>
            MeteringData(json, batchName, idx)
          }

  val getRecordHeaders: MeteringData => java.lang.Iterable[Header] = meteringData =>
    (dnwgHeaders ++ Map(
      "batch" -> meteringData.batch,
      "index" -> meteringData.index.toString
    )).map { case (k, v) =>
      new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8)).asInstanceOf[Header]
    }.asJava

  // generate the kafka message
  val createRecord: MeteringData => ProducerRecord[String, String] = meteringData =>
    new ProducerRecord[String, String](kafkaTopic, null, null, null, meteringData.json, getRecordHeaders(meteringData))

  // produce a bunch of records per chunk on within a transaction
  val produceRecords: Transaction => Chunk[ProducerRecord[String, String]] => RIO[ZEnv with Has[TransactionalProducer], Chunk[RecordMetadata]] =
    transaction => chunk => transaction.produceChunk(chunk, Serde.string, Serde.string, None)

  // BETTER ERRORS

  // Tries to get the json error message, otherwise just the original message
  val apiErrorJson: String => Exception = json => new Exception(Try((parse(json) \\ "error").extract[String]).getOrElse(json))

  // Better verbosity
  val wrapException: String => Throwable => Throwable = message => ex => new Exception(s"$message: ${ex.getMessage}", ex)

  // This will return the meteringpoints as a list of points as json strings from the endpoint. No type checking at t
  val getMeteringPoints: ZIO[SttpClient with Logging, Throwable, List[MeteringData]] = for {
    fromDateTime <- ZIO.succeed(getOffset)
    toDateTime   <- ZIO.succeed(fromDateTime.plusHours(1))
    _            <- log.debug(s"From date $fromDateTime - $toDateTime")
    response     <- send(getAllRequest(fromDateTime)(toDateTime)).mapError(wrapException("API call failed"))
    body         <- ZIO.fromEither(response.body).mapError(apiErrorJson)
    _            <- log.debug(s"Received ${body.length} bytes")
    json         <- ZIO(parse(body)).mapError(wrapException("Parsing json body failed"))
    meteringPoints <- ZIO(extractMeteringPoints(s"$fromDateTime-$toDateTime")(json))
                        .mapError(wrapException("Extracting json body failed"))
    _ <- log.debug(s"Received ${meteringPoints.size} items from endpoint")
  } yield meteringPoints

  val program = for {
    meteringPoints <- getMeteringPoints
    _ <- TransactionalProducer.createTransaction.use { transaction =>
           ZStream
             .fromIterable(meteringPoints)
             .map(createRecord)
             .mapChunksM(produceRecords(transaction))
             .runDrain
         }
    _ <-
      log.debug(
        s"Wrote ${meteringPoints.size} to kafka topic ${kafkaTopic} using batch ${meteringPoints.headOption.map(_.batch).getOrElse("N/A")}"
      )
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    program
      .foldM(
        error =>
          log
            .error("Error: " + error.getMessage)
            .as(ExitCode(1)),
        _ =>
          log
            .info("Success")
            .as(ExitCode(0))
      )
      .provideCustomLayer(allLayers)
      .retryN(3)
      .orDie
  }
}
