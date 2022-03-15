package services

import cdf._
import com.typesafe.config.ConfigFactory
import zio._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.typesafe._

// App specific config
case class AppConfig(
  name: String,
//  logLevel: LogLevel,
  dnwgApi: dnwg.Config,
  offset: services.offset.Config,
  kafka: services.kafka.Config
)

object AppConfig {

  private val descriptor = DeriveConfigDescriptor.descriptor[AppConfig]

  val live: ZLayer[Any, Exception, AppConfig] = TypesafeConfig
    .fromTypesafeConfig(ConfigFactory.load.getConfig("app").resolve, descriptor)
    .mapError(e => new Exception(e.getMessage()))

  def name: ZIO[AppConfig, Throwable, String] =
    ZIO.serviceWithZIO(cfg => ZIO.succeed(cfg.name))

}
