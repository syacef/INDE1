name := "srvc-io"
version := "0.1.1"

scalaVersion := "2.13.16"

val akkaVersion = "2.6.20"
val akkaHttpVersion = "10.2.10"
val kafkaVersion = "3.4.0"

Compile / scalaSource := baseDirectory.value / "src" / "main" / "scala"
Compile / resourceDirectory := baseDirectory.value / "src" / "main" / "resources"
Test / scalaSource := baseDirectory.value / "src" / "test" / "scala"
Test / resourceDirectory := baseDirectory.value / "src" / "test" / "resources"

ThisBuild  / envFileName := ".env"

Test / envFileName := "test.env"

Test / envVars := (Test / envFromFile).value

Test / parallelExecution := false
Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD")

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scalacOptions ++= Seq(
    "-Wunused:imports",
    "-Ywarn-unused:imports"
)
Compile / compile / wartremoverWarnings ++= Warts.unsafe
Test / compile / wartremoverWarnings ++= Warts.unsafe

addCommandAlias("lint", "; scalafmtCheck; test:scalafmtCheck; scalafix --check; test:scalafix --check")
addCommandAlias("fix", "; scalafmt; test:scalafmt; scalafix; test:scalafix")

assemblyMergeStrategy in assembly := {
    case PathList("META-INF", _*) => MergeStrategy.discard
    case _                        => MergeStrategy.first
}

target := baseDirectory.value / "target"

libraryDependencies ++= Seq(
    // Akka libraries
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,

    // Kafka libraries
    "org.apache.kafka" % "kafka-clients" % kafkaVersion,
    "org.apache.kafka" %% "kafka-streams-scala" % kafkaVersion,

    // Circe for JSON handling
    "io.circe" %% "circe-core" % "0.14.5",
    "io.circe" %% "circe-generic" % "0.14.5",
    "io.circe" %% "circe-parser" % "0.14.5",

    // Configuration library
    "com.typesafe" % "config" % "1.4.2",

    // FS2 for functional streaming
    "co.fs2" %% "fs2-core" % "3.12.0",
    "com.github.cb372" %% "cats-retry" % "3.1.0" exclude("org.typelevel", "cats-effect_2.13"),
    "org.typelevel" %% "cats-effect" % "3.6.1",
    "com.github.fd4s" %% "fs2-kafka" % "3.8.0",

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
    "org.typelevel" %% "cats-effect-testing-scalatest" % "1.6.0" % Test
)
