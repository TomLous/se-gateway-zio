package service

import model.DNWGResponse._
import org.json4s._
import org.json4s.native.JsonMethods._
import sttp.client3._
import sttp.client3.httpclient.zio.SttpClient
import zio._
import zio.duration._
import zio.logging.{Logging, log}

import java.time.LocalDate
import scala.util.Try

object DNWGApi {

  trait Service {
    def getAllRequest(fromDate: LocalDate, toDate: LocalDate): ZIO[Logging, DNWGApiError, List[MeteringPointData]]
    def getMeteringPoints: ZIO[Logging, DNWGApiError, List[MeteringPoint]]
  }

  val live: ZLayer[Has[DNWGApi.Config] with Has[SttpClient.Service], DNWGApiError, Has[DNWGApi.Service]] =
    ZLayer.fromServices[DNWGApi.Config, SttpClient.Service, DNWGApi.Service] { (config, backend) =>
      DNWGApiServiceLive(config, backend)
    }

  // Possible errors
  sealed trait DNWGApiError
  case class RequestError(error: String) extends DNWGApiError
  case class JSONError(error: String)    extends DNWGApiError

  // Config
  case class Config(token: String, readTimeout: Duration)

  // Error class
  case class ErrorMessage(error: String, code: Int)

  case class DNWGApiServiceLive(config: DNWGApi.Config, backend: SttpClient.Service) extends DNWGApi.Service {
    val host = "https://emi.dnwg.nl"

    private object LocalDateSerializer
        extends CustomSerializer[LocalDate](_ =>
          (
            { case JString(date) => LocalDate.parse(date) },
            { case date: LocalDate => JString(date.toString) }
          )
        )

    implicit val formats: Formats = DefaultFormats + LocalDateSerializer

    private def parseJson[T: Manifest](json: String): IO[JSONError, T] =
      ZIO
        .fromTry(
          Try(parse(json).extract[T])
        )
        .mapError(e => JSONError(e.getMessage))

    private def send[T: Manifest](request: Request[Either[String, String], Any]): ZIO[Logging, DNWGApiError, T] =
      for {
        _ <- log.debug(s"Calling endpoint ${request.uri.toString()}")
        response <- backend
                      .send(request)
                      .orDie
        _ <- log.debug(s"Success: ${response.code.isSuccess}")
        body <- ZIO
                  .fromEither(response.body)
                  .mapError(apiRequestErrorJson)
        _    <- log.debug(s"Byte length: ${body.length}")
        json <- parseJson[T](body)
        _    <- log.debug(s"Content decoded")
      } yield json

    private val baseGet = basicRequest.auth
      .bearer(config.token)
      .readTimeout(config.readTimeout.asScala)

    // either get the json error message, or else just the plain error
    private def apiRequestErrorJson(content: String): DNWGApiError =
      Try(parse(content).extract[ErrorMessage])
        .fold(
          _ => RequestError(content),
          errorMessage => RequestError(errorMessage.error)
        )

    override def getMeteringPoints: ZIO[Logging, DNWGApiError, List[MeteringPoint]] = {
      val request = baseGet
        .get(
          uri"""$host/api/v1/meteringPoints"""
        )

      send[RawMeteringPoints](request)
        .map(_.data.items)
    }

    override def getAllRequest(fromDate: LocalDate, toDate: LocalDate): ZIO[Logging, DNWGApiError, List[MeteringPointData]] = {
      val request = baseGet
        .get(
          uri"""$host/api/v1/meteringPoints/all/meteringdata/interval?periodStartdate=$fromDate&periodEnddate=$toDate&extendedData=registerreading"""
        )

      send[RawAll](request)
        .map(_.data.items)
    }

  }

}
