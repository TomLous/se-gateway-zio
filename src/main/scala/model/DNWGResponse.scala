package model

import java.time.{Instant, LocalDate}

object DNWGResponse {
  case class RawAll(apiVersion: Int, method: String, data: RawData[MeteringPointData])
  case class RawMeteringPoints(apiVersion: Int, method: String, data: RawData[MeteringPoint])

  case class RawData[T](items: List[T])

  case class Discipline(id: String, description: String)
  case class MeteringPointType(id: String, description: String)

  case class ChannelData(channelID: String, meteringData: List[Measurement], description: String, direction: String)
  case class Measurement(
    value: Double,
    registerreading: Option[Int],
    tariffzone: Option[String],
    timestamp: Instant
  )

  case class Location(
    locationID: Long,
    name: Option[String],
    description: Option[String],
    address: String,
    zipcode: String,
    city: String
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

  case class MeteringPointData(
    meteringPointID: String,
    channels: List[ChannelData]
  )

}