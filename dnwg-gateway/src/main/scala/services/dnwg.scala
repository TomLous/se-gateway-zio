package services

import cdf.util.Json
import smartenergy.DNWGResponse._
import sttp.client3._
import sttp.client3.httpclient.zio.SttpClient
import zio._

import java.time.LocalDate

object dnwg {

  // This is the service definition. All Services (live, mock, etc) need to implement these methods
  trait DNWGApi {
    def getAllMeteringPointData(
      fromDate: LocalDate,
      toDate: LocalDate
    ): ZIO[Any, DNWGApiError, Iterable[MeteringPointData]]
    def getMeteringPoints: ZIO[Any, DNWGApiError, Iterable[MeteringPoint]]
  }

  object DNWGApi {

    // accessors (to make live easier)
    def getAllMeteringPointData(
      fromDate: LocalDate,
      toDate: LocalDate
    ): ZIO[DNWGApi, DNWGApiError, Iterable[MeteringPointData]] =
      ZIO.serviceWithZIO(_.getAllMeteringPointData(fromDate, toDate))

    def getMeteringPoints: ZIO[DNWGApi, DNWGApiError, Iterable[MeteringPoint]] =
      ZIO.serviceWithZIO(_.getMeteringPoints)
  }


  // Possible errors
  sealed abstract class DNWGApiError(error: String, cause: Option[Throwable] = None)
      extends Exception(error, cause.orNull)
  case class RequestError(error: String, cause: Option[Throwable] = None) extends DNWGApiError(error, cause)

  // Config for the API
  case class Config(token: String, readTimeout: Duration)

  // Error model as response from Service
  private case class ApiErrorMessage(error: String, code: Int)

  private case class NestedError(message: String, code: Int)
  private case class ApiErrorMessageAlternative(error: NestedError)

  object DNWGApiLive {
    val layer: ZLayer[Config with SttpClient, Throwable, DNWGApi] = (DNWGApiLive(_, _)).toLayer[DNWGApi]
  }

  // Implementation of the live service
  case class DNWGApiLive(config: Config, backend: SttpClient) extends DNWGApi {
    val host = "https://emi.tums-meetdiensten.nl" ///https://emi.dnwg.nl

    // Parse the json to the final class (manifest is needed, since this is a generic implementation)

    // Send the sttp request. It requires Logging to be in the env. Returns either a DNWGApiError or an object of type T
    private def send[T: Manifest](request: Request[Either[String, String], Any]): ZIO[Any, DNWGApiError, T] =
      ZIO.logSpan("action=api-request") {
        for {
          _ <- ZIO.logDebug(s"action=api-request uri=${request.uri.toString()}")
          response <- backend
            .send(request)
            .orDie
          body <- ZIO
            .fromEither(response.body)
            .foldZIO(apiRequestErrorJson, e => ZIO.succeed(e))
          _ <- ZIO.logDebug(s"action=api-request response-length=${body.length}")
          _ <- ZIO.logDebug(s"action=api-request response-length=${body}")
          json <- Json
            .parseJson[T](body)
            .mapError(e =>
              RequestError("action=api-request type=json-parse message='Error parsing response Json'", Some(e))
            )
        } yield json
      }

    // Basic get request config
    private val baseGet = basicRequest.auth
      .bearer(config.token)
      .readTimeout(config.readTimeout.asScala)

    // either get the json error message, or else just the plain error
    private def apiRequestErrorJson(content: String): ZIO[Any, DNWGApiError, Nothing] =
      Json
        .parseJson[ApiErrorMessage](content)
        .map(_.error)
        .orElse(
          Json
            .parseJson[ApiErrorMessageAlternative](content)
            .map(_.error.message)
        )
        .foldZIO(parseFail=>
          ZIO.fail(RequestError(content, Some(parseFail))),
          errorMessage => ZIO.fail(RequestError(errorMessage))
        )

    // Implements the service method. Needs Logging, returns DNWGApiError or a List[MeteringPoint]
    override def getMeteringPoints: ZIO[Any, DNWGApiError, Iterable[MeteringPoint]] = {
      val request = baseGet
        .get(uri"""$host/api/v1/meteringPoints""")

      send[RawMeteringPoints](request)
        .map(_.data.items)
    }

    // Implements the service method. Needs Logging, returns DNWGApiError or a List[MeteringPointData]
    override def getAllMeteringPointData(
      fromDate: LocalDate,
      toDate: LocalDate
    ): ZIO[Any, DNWGApiError, Iterable[MeteringPointData]] = {
      val request = baseGet
        .get(
          uri"""$host/api/v1/meteringPoints/all/meteringdata/interval?periodStartdate=$fromDate&periodEnddate=$toDate&extendedData=registerreading"""
        )

      send[RawAll](request)
        .map(_.data.items)
    }

  }

}
