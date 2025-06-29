name := "repo_account"
version := "0.1.2"

scalaVersion := "2.13.16"

val http4sVersion = "0.23.26"
val circeVersion = "0.14.5"

Compile / scalaSource := baseDirectory.value / "src" / "main" / "scala"
Compile / resourceDirectory := baseDirectory.value / "src" / "main" / "resources"
Test / scalaSource := baseDirectory.value / "src" / "test" / "scala"
Test / resourceDirectory := baseDirectory.value / "src" / "test" / "resources"

ThisBuild  / envFileName := ".env"


ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
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

assemblyMergeStrategy in assembly := {
    case PathList("META-INF", _*) => MergeStrategy.discard
    case _                        => MergeStrategy.first
}

target := baseDirectory.value / "target"

libraryDependencies ++= Seq(
    // HTTP4s for HTTP server
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-ember-server" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "org.http4s" %% "http4s-client" % http4sVersion,

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
    "com.github.fd4s" %% "fs2-kafka" % "3.8.0",

    // Redis client
    "redis.clients" % "jedis" % "4.3.1",
    //"org.typelevel" %% "cats-effect-redis" % "0.4.0",

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
