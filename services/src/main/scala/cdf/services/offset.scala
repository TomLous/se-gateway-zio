package cdf.services

import zio._

import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import scala.io.Source

// Possible errors

object offset {
  sealed abstract class OffsetError(error: String, cause: Option[Throwable] = None)
      extends Exception(error, cause.orNull)
  case class OffsetReadError(error: String, cause: Option[Throwable] = None)  extends OffsetError(error, cause)
  case class OffsetWriteError(error: String, cause: Option[Throwable] = None) extends OffsetError(error, cause)

  // Service Interface
  trait Offset {
    def getStartOffset: ZIO[Any, OffsetError, LocalDate]
    def getEndOffset(startDate: LocalDate): ZIO[Any, OffsetError, LocalDate]
    def setNextOffset(localDate: LocalDate): ZIO[Any, OffsetError, Unit]
  }

  // accessors
  object Offset {
    def getStartOffset: ZIO[Offset, OffsetError, LocalDate] =
      ZIO.serviceWithZIO(_.getStartOffset)

    def getEndOffset(localDate: LocalDate): ZIO[Offset, OffsetError, LocalDate] =
      ZIO.serviceWithZIO(_.getEndOffset(localDate))

    def setNextOffset(localDate: LocalDate): ZIO[Offset, OffsetError, Unit] =
      ZIO.serviceWithZIO(_.setNextOffset(localDate))
  }

  // Config for the API
  case class Config(path: String, defaultOffset: LocalDate, offsetRange: Duration)

  // This is the live Service definition. To create the service we need Config, A SttpClient. The service always succeeds
  case class OffsetLive(config: Config) extends Offset {
    override def getStartOffset: ZIO[Any, OffsetError, LocalDate] =
      for {
        _ <- ZIO.logDebug(s"type=offset-start action=read file=${config.path}")
        data <- ZIO(Source.fromFile(config.path)(StandardCharsets.UTF_8))
                  .map(_.mkString)
                  .tapError(_ =>
                    ZIO.logWarning(s"type=offset-start message='No offset found, using default: ${config.defaultOffset}'")
                  )
                  .fold(_ => config.defaultOffset.toString, offset => offset.trim)
        date <- ZIO(LocalDate.parse(data))
                  .mapError(e => OffsetReadError("type=offset-start message='Failed to read offset'", Some(e)))
        _ <- ZIO.logDebug(s"type=offset-start action=read value=$date")
      } yield date

    override def getEndOffset(localDate: LocalDate): ZIO[Any, OffsetError, LocalDate] = for {
      date <- ZIO(localDate.plusDays(config.offsetRange.toDays)).mapError(e =>
                OffsetReadError(s"Can't create date from range: ${config.offsetRange}", Some(e))
              )
      _ <- ZIO.logDebug(s"type=offset-end action=calculate value=$date")
    } yield date

    override def setNextOffset(localDate: LocalDate): ZIO[Any, OffsetError, Unit] =
      (for {
        _ <- ZIO.logDebug(s"type=offset-next action=write value=$localDate file=${config.path}").toManaged_
        writer <- ZManaged.fromAutoCloseable(
                    ZIO(new PrintWriter(config.path, StandardCharsets.UTF_8))
                      .mapError(t =>
                        OffsetWriteError(
                          s"type=offset-next file=${config.path} message='Failed to open offset writer'",
                          Some(t)
                        )
                      )
                  )
        _ <- ZIO(writer.print(localDate.toString))
               .mapError(t =>
                 OffsetWriteError(
                   s"type=offset-next file=${config.path} message='Failed to write offset' value=$localDate",
                   Some(t)
                 )
               )
               .toManaged
      } yield ()).useNow

  }

  object OffsetLive {
    val layer: ZLayer[Config, Nothing, Offset] = (OffsetLive(_)).toLayer[Offset]
  }
}
