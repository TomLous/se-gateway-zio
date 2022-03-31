import Dependencies._
import com.typesafe.sbt.packager.docker.Cmd
import sbt.Keys.libraryDependencies
lazy val scala213  = "2.13.8"
lazy val mainScala = scala213
lazy val allScala  = Seq(scala213)

// Graal/JVM stuff. Needs to be available here https://github.com/orgs/graalvm/packages/container/graalvm-ce/versions
val oracleLinux  = "8"
val jvmVersion   = "17"
val graalVersion = "22"

// Variables
lazy val baseName       = "smartenergy"
lazy val baseImage      = "alpine:latest" //3.15.0
lazy val dockerBasePath = "/opt/docker/bin"

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
  trapExit                 := false,
  scalafmtOnCompile        := true
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

lazy val graalDockerSettings = Seq(
  GraalVMNativeImage / containerBuildImage := GraalVMNativeImagePlugin
    .generateContainerBuildImage(s"ghcr.io/graalvm/graalvm-ce:ol$oracleLinux-java$jvmVersion-$graalVersion")
    // docker manifest inspect ghcr.io/graalvm/graalvm-ce:ol8-java17-22 | jq -r '.manifests[] | select(.platform.architecture == "amd64") | .digest'
    .value,
  graalVMNativeImageOptions := Seq(
    "--verbose",
    "--no-fallback",
    "--install-exit-handlers",
    "--enable-http",
    "--allow-incomplete-classpath",
    "--report-unsupported-elements-at-runtime",
    "-H:+StaticExecutableWithDynamicLibC",
    "-H:+RemoveSaturatedTypeFlows",
    "-J-Xmx10G", // TODO Make sure this matches the docker mem available. Also
    "-H:EnableURLProtocols=http",
    "-H:EnableURLProtocols=https",
    "-H:+ReportExceptionStackTraces",
    "-H:-ThrowUnsafeOffsetErrors",
    "-H:+PrintClassInitialization"
  ),
  dockerBaseImage := baseImage,
  dockerCommands ++= Seq(
    Cmd("USER", "root"),
    Cmd("RUN", "apk update && apk add gcompat"
//      Cmd("COPY","opt/docker/conf/application.conf","/opt/docker/conf/application.conf"),)
  ),
//    dockerChmodType := DockerChmodType.Custom("ugo=rwX"),
//    dockerAdditionalPermissions += (DockerChmodType.Custom(
//      "ugo=rwx"
//    ), dockerBinaryPath.value),
  Docker / mappings := Seq(
    ((GraalVMNativeImage / target).value / name.value) -> s"$dockerBasePath/app"
  ),
  dockerEntrypoint := Seq(s"$dockerBasePath/app"),
  dockerRepository := sys.env.get("DOCKER_REPOSITORY"),
  dockerAlias := DockerAlias(
    dockerRepository.value,
    dockerUsername.value,
    //    s"${organizationName.value}/$baseName-${name.value}".toLowerCase,
    s"$baseName-${name.value}".toLowerCase,
    Some(version.value)
  )
  //  dockerUpdateLatest := true,
  //  dockerUsername     := sys.env.get("DOCKER_USERNAME").map(_.toLowerCase)
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
      libraryDependencies ++= Zio.deps ++ Json.deps ++ Logging.deps ++ Kafka.deps ++ Avro.deps ++ Http.deps
    )
    .dependsOn(util)

lazy val dnwgGateway =
  project
    .in(file("dnwg-gateway"))
    .enablePlugins(GraalVMNativeImagePlugin, DockerPlugin)
//    .enablePlugins(BuildInfoPlugin)
    .settings(
      name := "dnwg-gateway",
      commonSettings,
      testSettings,
      graalDockerSettings,
//      dockerBinaryPath    := s"$dockerBasePath/dnwg-gateway",
      Compile / mainClass := Some("ManualBackfill"),
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
      name := "dnwg-transformer",
      commonSettings,
      testSettings,
      libraryDependencies ++= Zio.deps ++ Json.deps ++ Logging.deps ++ Config.deps ++ Kafka.deps ++ Http.deps ++ Avro.deps
    )
    .dependsOn(model)
    .dependsOn(util)
    .dependsOn(services)

lazy val dockerBinaryPath = settingKey[String]("Get the docker path")
