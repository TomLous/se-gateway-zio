/** MACHINE-GENERATED FROM AVRO SCHEMA. DO NOT EDIT DIRECTLY */
package smartenergy

final case class channel(
  msg_id: String,
  channel_id: String,
  unit: Option[String] = None,
  direction: Option[String] = None,
  description: String,
  channel_type: String,
  source: String,
  last_updated: java.time.Instant
)
