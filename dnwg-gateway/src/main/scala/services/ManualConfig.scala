package services

import com.typesafe.config.ConfigFactory
import zio.ZLayer
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.typesafe.TypesafeConfig

import java.time.LocalDate

case class ManualConfig(
  meteringPointId: String,
  offsetDate: LocalDate
)

object ManualConfig {

  private val descriptor = DeriveConfigDescriptor.descriptor[ManualConfig]

  val live: ZLayer[Any, Exception, ManualConfig] = TypesafeConfig
    .fromTypesafeConfig(ConfigFactory.load.getConfig("manual").resolve, descriptor)
    .mapError(e => new Exception(e.getMessage()))

}
