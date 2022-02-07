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

  // This is the service definition. All Services (live, mock, etc) need to implement these methods
  trait Service {
    def getAllRequest(fromDate: LocalDate, toDate: LocalDate): ZIO[Logging, DNWGApiError, List[MeteringPointData]]
    def getMeteringPoints: ZIO[Logging, DNWGApiError, List[MeteringPoint]]
  }

  // accessors (to make live easier)
  def getAllRequest(fromDate: LocalDate, toDate: LocalDate): ZIO[Has[DNWGApi.Service] with Logging, DNWGApiError, List[MeteringPointData]] =
    ZIO.accessM(_.get.getAllRequest(fromDate, toDate))

  def getMeteringPoints: ZIO[Has[DNWGApi.Service] with Logging, DNWGApiError, List[MeteringPoint]] =
    ZIO.accessM(_.get.getMeteringPoints)

  // This is the live Service definition. To create the service we need Config, A SttpClient. The service always succeeds
  val live: ZLayer[Has[DNWGApi.Config] with Has[SttpClient.Service], Nothing, Has[DNWGApi.Service]] =
    // Config + Sttpclient => API Service
    ZLayer.fromServices[DNWGApi.Config, SttpClient.Service, DNWGApi.Service] { (config, backend) =>
      DNWGApiServiceLive(config, backend)
    }

  // Possible errors
  sealed abstract class DNWGApiError(error: String, cause: Option[Throwable]=None) extends Exception(error, cause.orNull)
  case class RequestError(error: String, cause: Option[Throwable]=None) extends DNWGApiError(error, cause)
  case class JSONError(error: String, cause: Option[Throwable]=None)   extends DNWGApiError(error, cause)

  // Config for the API
  case class Config(token: String, readTimeout: Duration)

  // Error model as response from Service
  private case class ApiErrorMessage(error: String, code: Int)

  // Implementation of the live service
  case class DNWGApiServiceLive(config: DNWGApi.Config, backend: SttpClient.Service) extends DNWGApi.Service {
    val host = "https://emi.dnwg.nl"

    // Needed for the json parsing (maybe move to helper class if reused)
    private object LocalDateSerializer
        extends CustomSerializer[LocalDate](_ =>
          (
            { case JString(date) => LocalDate.parse(date) },
            { case date: LocalDate => JString(date.toString) }
          )
        )

    // json4s loves implicits
    implicit val formats: Formats = DefaultFormats + LocalDateSerializer

    // Parse the json to the final class (manifest is needed, since this is a generic implementation)
    private def parseJson[T: Manifest](json: String): IO[JSONError, T] =
      ZIO
        .fromTry(
          Try(parse(json).extract[T])
        )
        .mapError(e => JSONError("Error while parsing json", Some(e)))

    // Send the sttp request. It requires Logging to be in the env. Returns either a DNWGApiError or an object of type T
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

    // Basic get request config
    private val baseGet = basicRequest.auth
      .bearer(config.token)
      .readTimeout(config.readTimeout.asScala)

    // either get the json error message, or else just the plain error
    private def apiRequestErrorJson(content: String): DNWGApiError =
      Try(parse(content).extract[ApiErrorMessage])
        .fold(
          e => RequestError("Can't parse error json", Some(e)),
          errorMessage => RequestError(errorMessage.error)
        )

    // Implements the service method. Needs Logging, returns DNWGApiError or a List[MeteringPoint]
    override def getMeteringPoints: ZIO[Logging, DNWGApiError, List[MeteringPoint]] = {
      val request = baseGet
        .get(uri"""$host/api/v1/meteringPoints""")

      send[RawMeteringPoints](request)
        .map(_.data.items)
    }

    // Implements the service method. Needs Logging, returns DNWGApiError or a List[MeteringPointData]
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
