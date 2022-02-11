package util

import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write
import org.json4s._
import zio.ZIO

import java.time.LocalDate

object Json {

  // json4s loves implicits
  implicit val formats: Formats = DefaultFormats + LocalDateSerializer

  case class JSONError(error: String, cause: Option[Throwable] = None) extends Exception(error, cause.orNull)

  // Needed for the json parsing (maybe move to helper class if reused)
  private object LocalDateSerializer
      extends CustomSerializer[LocalDate](_ =>
        (
          { case JString(date) => LocalDate.parse(date) },
          { case date: LocalDate => JString(date.toString) }
        )
      )

  def parseJson[T: Manifest](json: String): ZIO[Any, JSONError, T] =
    ZIO(
      parse(json).extract[T]
    ).mapError(e => JSONError("Error while parsing json", Some(e)))

  def renderJson[T: Manifest](item: T): ZIO[Any, JSONError, String] = {
    ZIO(
      write(item)
    ).mapError(e => JSONError("Error while rendering json", Some(e)))
  }

}
