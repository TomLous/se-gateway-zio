package service

import model.DNWGResponse._
import service.DNWGApi.{DNWGApiError, ErrorMessage, JSONError, RequestError}
import sttp.client3._
import zio._
import zio.duration._
import zio.json._

import java.time.LocalDate

object DNWGApi {
  // Service definition
  type DNWGApi = Has[Service]

  trait Service {
    def getAllRequest(fromDate: LocalDate, toDate: LocalDate): IO[DNWGApiError, List[MeteringPointData]]
    def getMeteringPoints: IO[DNWGApiError, List[MeteringPoint]]
  }

  // Possible errors
  sealed trait DNWGApiError
  case class RequestError(error: String) extends DNWGApiError
  case class JSONError(error: String)    extends DNWGApiError

  // Config
  case class Config(token: String, readTimeout: Duration)

  // Error class
  case class ErrorMessage(error: String, code: Int)

  object ErrorMessage {
    implicit val decoder: JsonDecoder[ErrorMessage] = DeriveJsonDecoder.gen[ErrorMessage]
  }

}

case class DNWGApiLive(config: DNWGApi.Config, backend: SttpBackend[Task, Nothing]) extends DNWGApi.Service {
  val host = "https://emi.dnwg.nl"

  private def send[T](request: Request[Either[String, String], Any])(implicit decoder: JsonDecoder[T]): IO[DNWGApiError, T] =
    backend
      .send(request)
      .orDie
      .flatMap(response =>
        for {
          body <- ZIO
                    .fromEither(response.body)
                    .mapError(apiRequestErrorJson)
          json <- ZIO
                    .fromEither(body.fromJson[T](decoder))
                    .mapError(JSONError)
        } yield json
      )

  override def getMeteringPoints: IO[DNWGApiError, List[MeteringPoint]] = {
    val request: Request[Either[String, String], Any] = basicRequest.auth
      .bearer(config.token)
      .readTimeout(config.readTimeout.asScala)
      .get(
        uri"""$host/api/v1/meteringPoints"""
      )

    send(request)(DeriveJsonDecoder.gen[RawMeteringPoints])
      .map(_.data.items)
  }

  override def getAllRequest(fromDate: LocalDate, toDate: LocalDate): IO[DNWGApiError, List[MeteringPointData]] = {
    val request: Request[Either[String, String], Any] = basicRequest.auth
      .bearer(config.token)
      .readTimeout(config.readTimeout.asScala)
      .get(
        uri"""$host/api/v1/meteringPoints/all/meteringdata/interval?periodStartdate=$fromDate&periodEnddate=$toDate&extendedData=registerreading"""
      )

    send(request)(DeriveJsonDecoder.gen[RawAll])
      .map(_.data.items)
  }

  // either get the json error message, or else just the plain error
  val apiRequestErrorJson: String => DNWGApiError = content =>
    content
      .fromJson[ErrorMessage]
      .fold(
        _ => RequestError(content),
        errorMessage => RequestError(errorMessage.error)
      )

}
