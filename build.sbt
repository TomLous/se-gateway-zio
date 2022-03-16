import Dependencies._
lazy val scala213  = "2.13.8"
lazy val mainScala = scala213
lazy val allScala  = Seq(scala213)

lazy val commonSettings = Seq(
  organizationName         := "schiphol",
  organization             := "nl.schiphol.dna.cdf",
  homepage                 := Some(url("https://confluence.schiphol.nl/pages/viewpage.action?pageId=27935997")),
  useCoursier              := false,
  scalaVersion             := mainScala,
  crossScalaVersions       := allScala,
  Test / parallelExecution := false,
  Test / fork              := true,
  run / fork               := true,
  scalafmtOnCompile := true
)

lazy val testSettings = Seq(
  Test / testOptions += Tests.Argument("-oDT"),
  Test / parallelExecution := true,
  Test / testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
)

lazy val avroScalaGeneratorSettings = Seq(
  Compile / sourceGenerators += (Compile / avroScalaGenerate).taskValue,
  Test / sourceGenerators += (Test / avroScalaGenerate).taskValue,
  (Compile / avroScalaSource) := new java.io.File(s"${baseDirectory.value}/src/main/scala")
)

lazy val root = (project in file("."))
  .aggregate(model, util, services, dnwgGateway)

lazy val util =
  project
    .in(file("util"))
    .settings(
      commonSettings,
      testSettings,
      libraryDependencies ++= Zio.deps ++ Json.deps ++ Logging.deps
    )

lazy val model =
  project
    .in(file("model"))
    .settings(
      commonSettings,
      avroScalaGeneratorSettings
    )
    .dependsOn(util)


lazy val services =
  project
    .in(file("services"))
    .settings(
      commonSettings,
      testSettings,
      libraryDependencies ++= Zio.deps ++ Json.deps ++  Logging.deps ++ Kafka.deps ++ Avro.deps ++ Http.deps
    )
    .dependsOn(util)

lazy val dnwgGateway =
  project
    .in(file("dnwg-gateway"))
//    .enablePlugins(BuildInfoPlugin)
    .settings(
      name              := "dnwg-gateway",
      commonSettings,
      testSettings,
      libraryDependencies ++= Zio.deps ++ Json.deps ++ Logging.deps ++ Config.deps ++ Kafka.deps ++ Http.deps
    )
    .dependsOn(model)
    .dependsOn(util)
    .dependsOn(services)


lazy val dnwgTransformer =
  project
    .in(file("dnwg-transformer"))
    //    .enablePlugins(BuildInfoPlugin)
    .settings(
      name              := "dnwg-transformer",
      commonSettings,
      testSettings,
      libraryDependencies ++= Zio.deps ++ Json.deps ++ Logging.deps ++ Config.deps ++ Kafka.deps ++ Http.deps ++ Avro.deps
    )
    .dependsOn(model)
    .dependsOn(util)
    .dependsOn(services)






//  .settings(
//    name := "se-gateway-zio",
//    libraryDependencies ++= Seq(
//      "dev.zio"                       %% "zio"                    % "1.0.13",
//      "dev.zio"                       %% "zio-streams"            % "1.0.12",
//      "dev.zio"                       %% "zio-kafka"              % "0.17.3",
//      "dev.zio"                       %% "zio-nio"                % "0.4.0",
//
//      "dev.zio"                       %% "zio-config"             % "1.0.9",
//      "dev.zio"                       %% "zio-config-magnolia"    % "1.0.9",
//      "dev.zio"                       %% "zio-config-typesafe"    % "1.0.9",
//
//      "dev.zio"                       %% "zio-logging-slf4j"      % "0.5.14",
//      "org.apache.logging.log4j"       % "log4j-core"             % "2.17.0",
//      "org.apache.logging.log4j"       % "log4j-slf4j-impl"       % "2.17.0",
//      "com.lmax"                       % "disruptor"              % "3.4.4",
//
//      "dev.zio"                       %% "zio-json"               % "0.1.5",
//      "org.json4s"                    %% "json4s-native"          % "4.0.2",
//
//      "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.3.17",
//
//      "dev.zio"                       %% "zio-test"               % "1.0.13" % Test
//    ),
//    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
//  )
