package service

import com.typesafe.config.ConfigFactory
import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.typesafe.TypesafeConfigSource
import zio.{Has, ULayer, ZIO}

case class AppConfig(
  name: String,
  dnwgApi: DNWGApi.Config,
  offset: Offset.Config
)

object AppConfig {

  private val descriptor = DeriveConfigDescriptor.descriptor[AppConfig]

  val live: ULayer[Has[AppConfig]] =
    (for {
      rawConfig <- ZIO.effect(ConfigFactory.load())
      source    <- ZIO.fromEither(TypesafeConfigSource.fromTypesafeConfig(rawConfig))
      config    <- ZIO.fromEither(read(descriptor.from(source)))
    } yield config).toLayer.orDie

}
