name := "srvc-notifier"
version := "0.1.0"

scalaVersion := "2.13.10"

val akkaVersion = "2.6.20"
val akkaHttpVersion = "10.2.10"
val kafkaVersion = "3.4.0"
val logbackVersion = "1.4.11"

Compile / scalaSource := baseDirectory.value / "src" / "main" / "scala"
Compile / resourceDirectory := baseDirectory.value / "src" / "main" / "resources"
Test / scalaSource := baseDirectory.value / "src" / "test" / "scala"
Test / resourceDirectory := baseDirectory.value / "src" / "test" / "resources"

target := baseDirectory.value / "target"

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,

    "org.apache.kafka" % "kafka-clients" % kafkaVersion,
    "org.apache.kafka" %% "kafka-streams-scala" % kafkaVersion,

    "io.circe" %% "circe-core" % "0.14.5",
    "io.circe" %% "circe-generic" % "0.14.5",
    "io.circe" %% "circe-parser" % "0.14.5",

    "com.typesafe" % "config" % "1.4.2",

    "ch.qos.logback" % "logback-classic" % "1.4.11",

    "org.scalatest" %% "scalatest" % "3.2.17" % Test
)
