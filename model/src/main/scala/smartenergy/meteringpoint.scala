/** MACHINE-GENERATED FROM AVRO SCHEMA. DO NOT EDIT DIRECTLY */
package smartenergy

final case class meteringpoint(
  msg_id: String,
  metering_point_id: String,
  meter_field_number: Option[String] = None,
  meter_sequence_number: Option[String] = None,
  ean: Option[String] = None,
  ean_country_code: Option[String] = None,
  ean_company_code: Option[String] = None,
  ean_sequence_number: Option[String] = None,
  ean_check_number: Option[String] = None,
  ean_location_code: Option[String] = None,
  discipline: Option[String] = None,
  discipline_description: Option[String] = None,
  metering_point_type: Option[String] = None,
  metering_point_type_description: Option[String] = None,
  name: Option[String] = None,
  metering_data_available_from: Option[java.time.Instant] = None,
  metering_data_available_till: Option[java.time.Instant] = None,
  location_id: Option[String] = None,
  channels: String,
  meter_virtual_physical: String,
  related_metering_point_id: Option[String] = None,
  source: String,
  last_updated: java.time.Instant
)
