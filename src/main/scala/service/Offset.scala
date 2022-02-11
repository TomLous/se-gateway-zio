package service

import zio._
import zio.duration.Duration
import zio.logging._

import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import scala.io.Source

object Offset {

  // This is the service definition. All Services (live, mock, etc) need to implement these methods
  trait Service {
    def getStartOffset: ZIO[Logging, OffsetError, LocalDate]
    def getEndOffset(startDate: LocalDate): ZIO[Logging, OffsetError, LocalDate]
    def setNextOffset(localDate: LocalDate): ZIO[Logging, OffsetError, Unit]
  }

  // accessors
  def getStartOffset: ZIO[Has[Offset.Service] with Logging, OffsetError, LocalDate] =
    ZIO.accessM(_.get.getStartOffset)

  def getEndOffset(localDate: LocalDate): ZIO[Has[Offset.Service] with Logging, OffsetError, LocalDate] =
    ZIO.accessM(_.get.getEndOffset(localDate))

  def setNextOffset(localDate: LocalDate): ZIO[Has[Offset.Service] with Logging, OffsetError, Unit] =
    ZIO.accessM(_.get.setNextOffset(localDate))

  // Possible errors
  sealed abstract class OffsetError(error: String, cause: Option[Throwable]=None) extends Exception(error, cause.orNull)
  case class OffsetReadError(error: String, cause: Option[Throwable]=None)  extends OffsetError(error, cause)
  case class OffsetWriteError(error: String, cause: Option[Throwable]=None) extends OffsetError(error, cause)

  // Config for the API
  case class Config(path: String, defaultOffset: LocalDate, offsetRange: Duration)

  // This is the live Service definition. To create the service we need Config, A SttpClient. The service always succeeds
  val live: ZLayer[Has[Offset.Config], Nothing, Has[Offset.Service]] =
    // Config => Offset Service
    ZLayer.fromService[Offset.Config, Offset.Service] {
      OffsetServiceLive
    }


  case class OffsetServiceLive(config: Offset.Config) extends Offset.Service {
    override def getStartOffset: ZIO[Logging, OffsetError, LocalDate] =
      for {
        _ <- log.debug(s"type=offset-start action=read file=${config.path}")
        data <- ZIO(Source.fromFile(config.path)(StandardCharsets.UTF_8))
                  .map(_.mkString)
                  .tapError(_ => log.warn(s"type=offset-start message='No offset found, using default: ${config.defaultOffset}'"))
                  .fold(_ => config.defaultOffset.toString, offset => offset.trim)
        date <- ZIO(LocalDate.parse(data))
                  .mapError(e => OffsetReadError("type=offset-start message='Failed to read offset'", Some(e)))
        _ <- log.debug(s"type=offset-start action=read value=$date")
      } yield date

    override def getEndOffset(localDate: LocalDate): ZIO[Logging, OffsetError, LocalDate] = for {
      date <- ZIO(localDate.plusDays(config.offsetRange.toDays)).mapError(e => OffsetReadError(s"Can't create date from range: ${config.offsetRange}", Some(e)))
      _ <- log.debug(s"type=offset-end action=calculate value=$date")
    } yield date

    override def setNextOffset(localDate: LocalDate): ZIO[Logging, OffsetError, Unit] =
      (for {
        _ <- log.debug(s"type=offset-next action=write value=$localDate file=${config.path}").toManaged_
        writer <- ZManaged.fromAutoCloseable(
                    ZIO(new PrintWriter(config.path, StandardCharsets.UTF_8))
                      .mapError(t => OffsetWriteError(s"type=offset-next file=${config.path} message='Failed to open offset writer'", Some(t)))
                  )
        _ <- ZIO(writer.print(localDate.toString))
               .mapError(t => OffsetWriteError(s"type=offset-next file=${config.path} message='Failed to write offset' value=$localDate", Some(t)))
               .toManaged_
      } yield ()).useNow

  }

}
