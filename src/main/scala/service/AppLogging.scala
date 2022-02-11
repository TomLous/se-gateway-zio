package service

import zio.clock.Clock
import zio.console.Console
import zio.logging._
import zio.{Has, ZLayer}

// App specific config
object AppLogging {

  // live creates a pure layer based on other available services (AppConfig, Console, Clock)
  val live: ZLayer[Has[AppConfig] with Console with Clock, Nothing, Logging] = for {
    hasConfig <- ZLayer.service[AppConfig]
    hasLogger <- Logging.console(
                   logLevel = hasConfig.get.logLevel,
                   format = LogFormat.ColoredLogFormat()
                 ) >>> Logging.withRootLoggerName(s"app=${hasConfig.get.name}")
  } yield hasLogger


  // layer will create a complete layer based on most common services
  val defautLayer: ZLayer[Any, Nothing, Logging] = (Console.live ++ Clock.live ++ AppConfig.live) >>> AppLogging.live

}
