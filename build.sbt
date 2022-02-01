ThisBuild / scalaVersion     := "2.13.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "nl.schiphol.dna.cdf"
ThisBuild / organizationName := "schiphol"

lazy val root = (project in file("."))
  .settings(
    name := "se-gateway-zio",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "1.0.13",
      "dev.zio" %% "zio-json" % "0.1.5",
      "dev.zio" %% "zio-streams" % "1.0.12",
      "dev.zio" %% "zio-kafka"   % "0.17.3",
      "org.json4s" %% "json4s-native"% "4.0.2",
      "dev.zio" %% "zio-logging-slf4j" % "0.5.14",
      "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.3.17",
      "dev.zio" %% "zio-test" % "1.0.13" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
