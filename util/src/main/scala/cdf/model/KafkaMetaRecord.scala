package cdf.model

import java.time.Instant

case class KafkaMetaRecord[+T](
  source: String,
  data: T,
  key: Option[String] = None,
  version:  Int = 1,
  dataTimestamp: Option[Instant] = None,
  ingestedTimestamp: Option[Instant] = None
) {

  def headers: Map[String, String] = {
    Map(
      "ce_specversion" -> "1.0",
      "ce_type"        -> this.getClass.getName.replace("$", "."),
      "ce_source"      -> source,
      "ce_time"        -> dataTimestamp.orElse(ingestedTimestamp).getOrElse(Instant.now()).toString,
      "content-type"   -> "application/json",
      "schema-version" -> version.toString
    ) ++ key.map(id => "ce_id" -> id)
  }


}
