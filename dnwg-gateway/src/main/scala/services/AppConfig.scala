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
    .fromTypesafeConfig(ConfigFactory.load.resolve, descriptor)
    .mapError(e => new Exception(e.getMessage()))

//    val result: Either[ReadError[String], MyConfig] =
//      configSource.flatMap(source => read(descriptor[MyConfig] from source)))

//    val live: ULayer[config.AppConfig] = configSource
//      (for {
//        rawConfig <- ZIO.attempt(ConfigFactory.load())
//        source    <- ZIO.fromEither(TypesafeConfigSource.fromTypesafeConfig(ConfigFactory.load.resolve))
//        config    <- ZIO.fromEither(read(descriptor.from(source)))
//      } yield config).toLayer.orDie

  def name: ZIO[AppConfig, Throwable, String] =
    ZIO.serviceWithZIO(cfg => ZIO.succeed(cfg.name))

}
