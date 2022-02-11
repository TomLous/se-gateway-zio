package service

import com.typesafe.config.ConfigFactory
import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.typesafe.TypesafeConfigSource
import zio.logging.LogLevel
import zio.{Has, ULayer, ZIO}


// App specific config
case class AppConfig(
  name: String,
  logLevel: LogLevel,
  dnwgApi: DNWGApi.Config,
  offset: Offset.Config,
  kafka: Kafka.Config
)

object AppConfig {

  private val descriptor = DeriveConfigDescriptor.descriptor[AppConfig]

  val live: ULayer[Has[AppConfig]] =
    (for {
      rawConfig <- ZIO.effect(ConfigFactory.load())
      source    <- ZIO.fromEither(TypesafeConfigSource.fromTypesafeConfig(rawConfig))
      config    <- ZIO.fromEither(read(descriptor.from(source)))
    } yield config).toLayer.orDie

  def name: ZIO[Has[AppConfig], Throwable, String] =
    ZIO.accessM(cfg => ZIO.succeed(cfg.get.name))



}
