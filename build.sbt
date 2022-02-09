ThisBuild / scalaVersion     := "2.13.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "nl.schiphol.dna.cdf"
ThisBuild / organizationName := "schiphol"

lazy val root = (project in file("."))
  .settings(
    name := "se-gateway-zio",
    libraryDependencies ++= Seq(
      "dev.zio"                       %% "zio"                    % "1.0.13",
      "dev.zio"                       %% "zio-streams"            % "1.0.12",
      "dev.zio"                       %% "zio-kafka"              % "0.17.3",
      "dev.zio"                       %% "zio-nio"                % "0.4.0",

      "dev.zio"                       %% "zio-config"             % "1.0.9",
      "dev.zio"                       %% "zio-config-magnolia"    % "1.0.9",
      "dev.zio"                       %% "zio-config-typesafe"    % "1.0.9",

      "dev.zio"                       %% "zio-logging-slf4j"      % "0.5.14",
      "org.apache.logging.log4j"       % "log4j-core"             % "2.17.0",
      "org.apache.logging.log4j"       % "log4j-slf4j-impl"       % "2.17.0",
      "com.lmax"                       % "disruptor"              % "3.4.4",

      "dev.zio"                       %% "zio-json"               % "0.1.5",
      "org.json4s"                    %% "json4s-native"          % "4.0.2",

      "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.3.17",

      "dev.zio"                       %% "zio-test"               % "1.0.13" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
