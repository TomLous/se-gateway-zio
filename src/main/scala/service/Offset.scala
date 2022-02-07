package service

import zio._
import zio.logging._

import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import scala.io.Source

object Offset {

  // This is the service definition. All Services (live, mock, etc) need to implement these methods
  trait Service {
    def getOffset: ZIO[Logging, OffsetError, LocalDate]
    def setOffset(localDate: LocalDate): ZIO[Logging, OffsetError, Unit]
  }

  // accessors
  def getOffset: ZIO[Has[Offset.Service] with Logging, OffsetError, LocalDate] =
    ZIO.accessM(_.get.getOffset)

  def setOffset(localDate: LocalDate): ZIO[Has[Offset.Service] with Logging, OffsetError, Unit] =
    ZIO.accessM(_.get.setOffset(localDate))

  // Possible errors
  sealed abstract class OffsetError(error: String, cause: Option[Throwable]=None) extends Exception(error, cause.orNull)
  case class OffsetReadError(error: String, cause: Option[Throwable]=None)  extends OffsetError(error, cause)
  case class OffsetWriteError(error: String, cause: Option[Throwable]=None) extends OffsetError(error, cause)

  // Config for the API
  case class Config(path: String, defaultOffset: LocalDate)

  // This is the live Service definition. To create the service we need Config, A SttpClient. The service always succeeds
  val live: ZLayer[Has[Offset.Config], Nothing, Has[Offset.Service]] =
    // Config => Offset Service
    ZLayer.fromService[Offset.Config, Offset.Service] {
      OffsetServiceLive
    }


  case class OffsetServiceLive(config: Offset.Config) extends Offset.Service {
    override def getOffset: ZIO[Logging, OffsetError, LocalDate] =
      for {
        _ <- log.debug(s"Reading file: ${config.path}")
        data <- ZIO(Source.fromFile(config.path)(StandardCharsets.UTF_8))
                  .map(_.mkString)
                  .tapError(_ => log.warn(s"No offset found, using default: ${config.defaultOffset}"))
                  .fold(_ => config.defaultOffset.toString, offset => offset)
        _ <- log.debug(s"Found data: $data")
        date <- ZIO(LocalDate.parse(data))
                  .mapError(e => OffsetReadError("Failed to read offset", Some(e)))
        _ <- log.debug(s"Offset: $date")
      } yield date

    override def setOffset(localDate: LocalDate): ZIO[Logging, OffsetError, Unit] =
      (for {
        _ <- log.debug(s"Writing $localDate to file: ${config.path}").toManaged_
        writer <- ZManaged.fromAutoCloseable(
                    ZIO(new PrintWriter(config.path, StandardCharsets.UTF_8))
                      .mapError(t => OffsetWriteError("Failed to open offset writer", Some(t)))
                  )
        _ <- log.debug("Opened offset writer").toManaged_
        _ <- ZIO(writer.print(localDate.toString))
               .mapError(t => OffsetWriteError("Failed to write offset", Some(t)))
               .toManaged_
      } yield ()).useNow

  }

}
