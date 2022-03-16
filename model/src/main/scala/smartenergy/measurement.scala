/** MACHINE-GENERATED FROM AVRO SCHEMA. DO NOT EDIT DIRECTLY */
package smartenergy

final case class measurement(
  msg_id: String,
  metering_point_id: String,
  channel_id: String,
  timestamp_ms: Long,
  timestamp: java.time.Instant,
  value: BigDecimal,
  register_reading: Option[BigDecimal] = None,
  period_covered_ms: Option[Long] = None,
  is_quality_disturbed: Option[Boolean] = None,
  is_metering_disturbed: Option[Boolean] = None,
  is_accurate: Option[Boolean] = None,
  is_corrected: Option[Boolean] = None,
  is_estimated: Option[Boolean] = None,
  source: String,
  last_updated: java.time.Instant
)
