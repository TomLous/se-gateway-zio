package smartenergy

import java.time.LocalDate

object DNWGResponse {
  case class RawAll(apiVersion: String, method: String, data: RawData[MeteringPointData])
  case class RawMeteringPoints(apiVersion: String, method: String, data: RawData[MeteringPoint])
  case class RawMeteringPointData(apiVersion: String, method: String, data: RawData[ChannelData])

  case class RawData[+T](items: List[T])

  case class Discipline(id: String, description: String)
  case class MeteringPointType(id: String, description: String)

  case class ChannelData(
    channelID: String,
    meteringdata: List[Measurement],
    description: Option[String],
    direction: Option[String]
  )

  case class Measurement(
    value: Double,
    registerreading: Option[Int],
    tariffzone: Option[String],
    timestamp: Long
  )

  case class Location(
    locationID: Long,
    name: Option[String],
    description: Option[String],
    address: Option[String],
    zipcode: Option[String],
    city: Option[String]
  )

  case class Channel(channelID: String, unit: String, description: String, direction: String)

  case class MeteringPoint(
    meteringPointID: String,
    ean: Option[String],
    discipline: Discipline,
    meteringPointType: MeteringPointType,
    name: String,
    meteringDataAvailableFrom: Option[LocalDate],
    meteringDataAvailableTill: Option[LocalDate],
    location: Location,
    channels: List[Channel]
  )

//    val msgId = s"$meteringPointID-$ean-$meteringPointType"

//    override val _key: Option[String] = Some(msgId)

  case class MeteringPointData(
    meteringPointID: String,
    channels: List[ChannelData]
  )

}
