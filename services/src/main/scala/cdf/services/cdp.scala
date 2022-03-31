package cdf.services

import cdf.util.Json
import org.apache.avro.reflect.AvroSchema
import sttp.client3._
import sttp.client3.httpclient.zio.SttpClient
import zio._

object cdp {

  // This is the service definition. All Services (live, mock, etc) need to implement these methods
  trait CDP {
    def getSchema(name: String): ZIO[Any, CDPError, AvroSchema] //AvroSchema
    def postData(data: String, schema: AvroSchema): ZIO[Any, CDPError, Unit]
  }

  object CDP {

//      ZIO.serviceWithZIO(_.getMeteringPoint(id))

//    def getMeteringPointData(
//      id: String,
//      fromDate: LocalDate,
//      optToDate: Option[LocalDate] = None
//    ): ZIO[DNWGApi, DNWGApiError, Iterable[ChannelData]] =
//      ZIO.serviceWithZIO(_.getMeteringPointData(id, fromDate, optToDate))
  }

  // Possible errors
  sealed abstract class CDPError(error: String, cause: Option[Throwable] = None) extends Exception(error, cause.orNull)
  case class RequestError(error: String, cause: Option[Throwable] = None)        extends CDPError(error, cause)

  // Config for the API
  case class Config(schemaAPIUrl: String, ingestionAPIUrl: String, apiKey: String)

  // Error model as response from Service
  private case class ApiErrorMessage(error: String, code: Int)

  private case class NestedError(message: String, code: Int)
  private case class ApiErrorMessageAlternative(error: NestedError)

  object CDPLive {
    val layer: ZLayer[Config with SttpClient, Throwable, CDP] = (CDPLive(_, _)).toLayer[CDP]
  }

  // Implementation of the live service
  case class CDPLive(config: Config, backend: SttpClient) extends CDP {

    // Parse the json to the final class (manifest is needed, since this is a generic implementation)

    // Send the sttp request. It requires Logging to be in the env. Returns either a DNWGApiError or an object of type T
    private def send[T: Manifest](request: Request[Either[String, String], Any]): ZIO[Any, CDPError, T] =
      ZIO.logSpan("action=cdp-request") {
        for {
          _ <- ZIO.logDebug(s"action=cdp-request uri=${request.uri.toString()}")
          response <- backend
                        .send(request)
                        .orDie
          body <- ZIO
                    .fromEither(response.body)
                    .foldZIO(apiRequestErrorJson, e => ZIO.succeed(e))
          _ <- ZIO.logDebug(s"action=cdp-request response-length=${body.length}")
          _ <- ZIO.logDebug(s"action=cdp-request response-length=${body}")
          json <- Json
                    .parseJson[T](body)
                    .mapError(e =>
                      RequestError("action=api-request type=json-parse message='Error parsing response Json'", Some(e))
                    )
        } yield json
      }

//    // Basic get request config
//    private val baseGet = basicRequest.auth
//      .bearer(config.token)
//      .readTimeout(config.readTimeout.asScala)
//
//    // either get the json error message, or else just the plain error
    private def apiRequestErrorJson(content: String): ZIO[Any, CDPError, Nothing] =
      Json
        .parseJson[ApiErrorMessage](content)
        .map(_.error)
        .orElse(
          Json
            .parseJson[ApiErrorMessageAlternative](content)
            .map(_.error.message)
        )
        .foldZIO(
          parseFail => ZIO.fail(RequestError(content, Some(parseFail))),
          errorMessage => ZIO.fail(RequestError(errorMessage))
        )

    // Implements the service method. Needs Logging, returns DNWGApiError or a List[MeteringPoint]
//    override def getMeteringPoints: ZIO[Any, DNWGApiError, Iterable[MeteringPoint]] = {
//      val request = baseGet
//        .get(uri"""$host/api/v1/meteringPoints""")
//
//      send[RawMeteringPoints](request)
//        .map(_.data.items)
//    }

    override def getSchema(name: String): ZIO[Any, CDPError, AvroSchema] = ???

    override def postData(data: String, schema: AvroSchema): ZIO[Any, CDPError, Unit] = ???
  }

}
