name := "srvc-stats"
version := "0.2.0"

scalaVersion := "2.12.18"

val sparkVersion = "3.5.5"
val hadoopVersion = "3.1.2"

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
    "-Ywarn-unused:imports"
)
Compile / compile / wartremoverWarnings ++= Warts.unsafe
Test / compile / wartremoverWarnings ++= Warts.unsafe

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
  "com.typesafe.slick" %% "slick" % "3.4.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.4.1",
  "com.typesafe" % "config" % "1.4.2",

  "com.fasterxml.jackson.core" % "jackson-databind" % "2.15.2",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.2",

  "org.apache.commons" % "commons-compress" % "1.24.0",

  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "com.squareup.okhttp3" % "okhttp" % "4.12.0",
  //"org.apache.hadoop" % "hadoop-aws" % "2.7.3",
  //"org.apache.hadoop" % "hadoop-common" % hadoopVersion,
  //"org.apache.hadoop" % "hadoop-mapreduce-client-core" % hadoopVersion,
  //"org.apache.hadoop" % "hadoop-client" % hadoopVersion,

  "io.minio" % "minio" % "8.5.7",

  "redis.clients" % "jedis" % "5.1.0"
)
