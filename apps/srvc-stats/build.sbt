name := "srvc-stats"
version := "0.1.0"

scalaVersion := "2.13.16"

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

assembly/assemblyMergeStrategy := {
    case PathList("META-INF", _*) => MergeStrategy.discard
    case _                        => MergeStrategy.first
}

target := baseDirectory.value / "target"

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.4.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.4.1",
  "org.postgresql" % "postgresql" % "42.6.0",
  "com.typesafe" % "config" % "1.4.2",
  "ch.qos.logback" % "logback-classic" % "1.4.11",

  "org.scalatest" %% "scalatest" % "3.2.17" % Test,

  "io.minio" % "minio" % "8.5.7",

  "com.fasterxml.jackson.core" % "jackson-databind" % "2.15.2",

  "org.apache.commons" % "commons-compress" % "1.24.0",

  "redis.clients" % "jedis" % "5.1.0"
)
