import sbt._

object Dependencies {

  trait Dep {
    val deps: Seq[ModuleID]
  }

  object Zio extends Dep {
    lazy val zioVersion    = "2.0.0-RC1"
    lazy val zioNioVersion = "2.0.0-RC3-1" //0.4.0"

    override val deps = Seq(
      "dev.zio" %% "zio"         % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio-nio"     % zioNioVersion,
      "dev.zio" %% "zio-test"    % zioVersion % Test
    )
  }

  object Logging extends Dep {
    lazy val zioLoggingVersion = "2.0.0-RC5" //0.5.14"
    lazy val log4jVersion      = "2.17.0"
    lazy val disruptorVersion  = "3.4.4"
    lazy val logbackVersion    = "1.2.11"

    val deps = Seq(
      "dev.zio"       %% "zio-logging-slf4j" % zioLoggingVersion,
      "ch.qos.logback" % "logback-classic"   % logbackVersion,
//      "org.apache.logging.log4j" % "log4j-core"        % log4jVersion,
//      "org.apache.logging.log4j" % "log4j-slf4j-impl"  % log4jVersion,
      "com.lmax" % "disruptor" % disruptorVersion
    )
  }

  object Config extends Dep {
    lazy val zioConfigVersion = "3.0.0-RC2"

    val deps = Seq(
      "dev.zio" %% "zio-config"          % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion
    )
  }

  object Http extends Dep {
    lazy val sttpVersion = "3.5.1"

    val deps = Seq(
      "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % sttpVersion
    )
  }

  object Kafka extends Dep {
//    lazy val zioKafkaVersion = "0.17.4"
    lazy val zioKafkaVersion = "2.0.0-M1"
//   lazy val kafkaVersion         = "2.8.1"
//   lazy val embeddedKafkaVersion = "2.8.1" // Should be the same as kafkaVersion, except for the patch part

    val deps = Seq(
      "dev.zio" %% "zio-kafka" % zioKafkaVersion
    )
  }

  object Json extends Dep {
    lazy val zioJsonVersion = "0.3.0-RC3" // 0.1.5"
    lazy val json4sVersion  = "4.0.4"

    val deps = Seq(
      "dev.zio"    %% "zio-json"      % zioJsonVersion,
      "org.json4s" %% "json4s-native" % json4sVersion
    )
  }

  /*
 lazy val zioVersion           = "1.0.13"
lazy val zioKafkaVersion      = "0.17.3"
lazy val zioConfigVersion      = "1.0.9"
lazy val zioLoggingVersion    = "0.5.14"
lazy val log4jVersion = "2.17.0"
lazy val zioJsonVersion       = "0.1.5"
lazy val json4sVersion       = "4.0.2"
lazy val kafkaVersion         = "2.8.1"




lazy val root = (project in file("."))
  .settings(
    name := "se-gateway-zio",
    libraryDependencies ++= Seq(
      "dev.zio"                       %% "zio"                    % "1.0.13",
      "dev.zio"                       %% "zio-streams"            % "1.0.12",
      "dev.zio"                       %% "zio-kafka"              % "0.17.3",
      "dev.zio"                       %% "zio-nio"                % "0.4.0",





      "dev.zio"                       %% "zio-json"               % "0.1.5",
      "org.json4s"                    %% "json4s-native"          % "4.0.2",

      "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.3.17",

      "dev.zio"                       %% "zio-test"               % "1.0.13" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
   */

}
