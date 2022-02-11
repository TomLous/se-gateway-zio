package model

import java.time.Instant

abstract class InternalSchemaRecord(
  val _key: Option[String] = None,
  val _version: Int = 1,
  val _timestamp: Option[Instant] = None
) {

  def _headers(source: String, ingestedAt: Instant): Map[String, String] = {
    Map(
      "ce_specversion" -> "1.0",
      "ce_type"        -> this.getClass.getName,
      "ce_source"      -> source,
      "ce_time"        -> _timestamp.getOrElse(ingestedAt).toString,
      "content-type"   -> "application/json",
      "schema-version" -> _version.toString
    ) ++ _key.map(id => "ce_id" -> id)
  }

}
