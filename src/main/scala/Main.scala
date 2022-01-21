import org.apache.kafka.clients.producer.ProducerConfig
import sttp.client3._
import sttp.client3.httpclient.zio.{HttpClientZioBackend, SttpClient, send}
import zio._
import zio.console.{Console, putStrLn}

import java.time.LocalDateTime
import zio.clock.Clock
import zio.logging._
import zio.system.System
import org.json4s._
import org.json4s.JsonAST.{JArray, JNothing, JObject}
import org.json4s.native.JsonMethods._

//import scala.concurrent.duration._
import zio.duration._
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.Try
import zio.kafka.producer._
import zio.kafka.serde._
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import sttp.capabilities.zio.ZioStreams
import zio.stream.ZStream

import java.nio.charset.{Charset, StandardCharsets}

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
  private val kafkaProducerSettings = ProducerSettings(List("localhost:29092","localhost:29093","localhost:29094"))
  private val kafkaTransactionalProducerSettings =
    TransactionalProducerSettings(kafkaProducerSettings, "dnwg-all-transaction")
  private val kafkaTopic = "test-topic"


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
//      Producer.make(kafkaProducerSettings))

  // PURE FUNCTIONS => NO SIDE EFFECTS
  val getOffset: LocalDateTime = LocalDateTime.parse("2022-01-18T22:00:00")

  val getAllRequest: LocalDateTime => LocalDateTime => Request[Either[String, String], Any] =
    fromIsoDate =>
      toIsoDate =>
        basicRequest.auth
          .bearer(dnwgApiToken)
          .readTimeout(dnwgApiReadTimeout.asScala)
          .get(
            uri"""$dnwgHost/api/v1/meteringPoints/all/meteringdata/interval?periodStartdate=$fromIsoDate&periodEnddate=$toIsoDate&extendedData=registerreading"""
          )

  val extractMeteringPoints: JValue => List[String] =
    json =>
      (json \\ "items")
        .extract[List[JObject]]
        .map(jval => compact(render(jval)))

  val getRecordHeaders: String => java.util.List[RecordHeader] =
    json => {
      dnwgHeaders.map { case (k, v) => new RecordHeader(k, v.getBytes("UTF-8")) }.toList.asJava
    }

  // BETTER ERRORS

  // Tries to get the json error message, otherwise just the original message
  val apiErrorJson: String => Exception = json =>
    new Exception(Try((parse(json) \\ "error").extract[String]).getOrElse(json))

  // Better verbosity
  val wrapException: String => Throwable => Throwable = message =>
    ex => new Exception(s"$message: ${ex.getMessage}", ex)

  //  val sendMeteringPoint: String => ZIO[SttpClient with Logging, Throwable, Unit] =
  //    jsonStr => {
  //      log.debug(jsonStr)
  //      // String topic, Integer partition, Long timestamp, K key, V value, Iterable<Header> headers
  //      val record  = new ProducerRecord(kafkaTopic, null, null, null, jsonStr, getHeaders(""))
  //      Producer.produce(record)
  //    }

  // This will return the meteringpoints as a list of points as json strings from the endpoint. No type checking at t
  val getMeteringPoints: ZIO[SttpClient with Logging, Throwable, List[String]] = for {
    fromDateTime   <- ZIO.succeed(getOffset)
    toDateTime     <- ZIO.succeed(fromDateTime.plusHours(1))
    _              <- log.debug(s"From date $fromDateTime - $toDateTime")
    response       <- send(getAllRequest(fromDateTime)(toDateTime)).mapError(wrapException("API call failed"))
    body           <- ZIO.fromEither(response.body).mapError(apiErrorJson)
    _              <- log.debug(s"Received ${body.length} bytes")
    json           <- ZIO(parse(body)).mapError(wrapException("Parsing json body failed"))
    meteringPoints <- ZIO(extractMeteringPoints(json)).mapError(wrapException("Extracting json body failed"))
    _              <- log.debug(s"Received ${meteringPoints.size} items from endpoint")
  } yield meteringPoints

  val createRecord: String => ProducerRecord[String, String] = json =>
    new ProducerRecord[String, String](kafkaTopic, null, null, null, json)

  val sendRecords
    : Transaction => Chunk[ProducerRecord[String, String]] => RIO[ZEnv with Has[TransactionalProducer], Chunk[RecordMetadata]] =
    transaction => chunk => transaction.produceChunk(chunk, Serde.string, Serde.string, None)

  val program = for {
    meteringPoints <- getMeteringPoints
    _ <- TransactionalProducer.createTransaction.use { transaction =>
           ZStream
             .fromIterable(meteringPoints)
//        .tap(json => log.debug(json))
             .map(createRecord)
             .mapChunksM(sendRecords(transaction))
             .tap(e => log.debug(e.toString))
             .runDrain
         }
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
      .orDie
  }
}
