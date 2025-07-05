name := "srvc-notifier"
version := "0.1.0"

scalaVersion := "2.13.16"

val akkaVersion = "2.6.20"
val akkaHttpVersion = "10.2.10"
val kafkaVersion = "4.0.0"
val circeVersion = "0.14.5"
val http4sVersion = "0.23.16"

Compile / scalaSource := baseDirectory.value / "src" / "main" / "scala"
Compile / resourceDirectory := baseDirectory.value / "src" / "main" / "resources"
Test / scalaSource := baseDirectory.value / "src" / "test" / "scala"
Test / resourceDirectory := baseDirectory.value / "src" / "test" / "resources"

ThisBuild  / envFileName := ".env"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalacOptions ++= Seq(
    "-Wunused:imports",
    "-Ywarn-unused:imports"
)
Compile / run / fork := true
Compile / compile / wartremoverWarnings ++= Warts.unsafe

Test / envFileName := "test.env"
Test / envVars := (Test / envFromFile).value
Test / parallelExecution := false
Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD")
Test / compile / wartremoverWarnings ++= Warts.unsafe
Test / fork := true

addCommandAlias("lint", "; scalafmtCheck; test:scalafmtCheck; scalafix --check; test:scalafix --check")
addCommandAlias("fix", "; scalafmt; test:scalafmt; scalafix; test:scalafix")

assembly/assemblyMergeStrategy := {
  case PathList("reference.conf") => MergeStrategy.concat
  case PathList("application.conf") => MergeStrategy.concat
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
  case PathList("META-INF", "maven", xs @ _*) => MergeStrategy.discard
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", "DEPENDENCIES") => MergeStrategy.discard
  case PathList("META-INF", "LICENSE") => MergeStrategy.discard
  case PathList("META-INF", "LICENSE.txt") => MergeStrategy.discard
  case PathList("META-INF", "NOTICE") => MergeStrategy.discard
  case PathList("META-INF", "NOTICE.txt") => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

target := baseDirectory.value / "target"

libraryDependencies ++= Seq(
    // HTTP4s for HTTP server
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-ember-server" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "org.http4s" %% "http4s-client" % http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,

    // Akka libraries
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,

    // Kafka libraries
    "org.apache.kafka" % "kafka-clients" % kafkaVersion,
    "org.apache.kafka" %% "kafka-streams-scala" % kafkaVersion,

    // Prometheus for alerts
    "org.dmonix" %% "prometheus-client-scala" % "1.0.0",
    "io.prometheus" % "prometheus-metrics-exporter-httpserver" % "1.3.8",
    "io.prometheus" % "simpleclient_hotspot" % "0.16.0",
    "io.prometheus" % "simpleclient_httpserver" % "0.16.0",
    "io.prometheus" % "simpleclient" % "0.16.0",

    // Circe for JSON handling
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,

    // Play JSON for additional JSON handling
    "com.typesafe.play" %% "play-json" % "2.9.4",

    // Configuration library
    "com.typesafe" % "config" % "1.4.2",

    // FS2 for functional streaming
    "co.fs2" %% "fs2-core" % "3.12.0",
    "com.github.cb372" %% "cats-retry" % "3.1.0" exclude("org.typelevel", "cats-effect_2.13"),
    "org.typelevel" %% "cats-effect" % "3.6.1",

    // Logging libraries
    "ch.qos.logback" % "logback-classic" % "1.4.14",
    "net.logstash.logback" % "logstash-logback-encoder" % "7.4",
    "org.slf4j" % "slf4j-api" % "2.0.9",

    "org.typelevel" %% "log4cats-slf4j" % "2.6.0",
    "org.typelevel" %% "log4cats-core" % "2.6.0",

    // Dotenv for environment variable management
    "io.github.cdimascio" % "java-dotenv" % "5.2.2",

    // Testing libraries
    "org.scalatest" %% "scalatest" % "3.2.15" % Test,
    "org.scalatestplus" %% "scalacheck-1-17" % "3.2.15.0" % Test,
    "org.scalatestplus" %% "mockito-4-6" % "3.2.15.0" % Test,
    "org.typelevel" %% "cats-effect-testing-scalatest" % "1.6.0" % Test,
    "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.40.17" % Test
)
