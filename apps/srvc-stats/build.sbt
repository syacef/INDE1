name := "srvc-stats"
version := "0.1.0"
scalaVersion := "2.13.10"

Compile / scalaSource := baseDirectory.value / "src" / "main" / "scala"
Compile / resourceDirectory := baseDirectory.value / "src" / "main" / "resources"
Test / scalaSource := baseDirectory.value / "src" / "test" / "scala"
Test / resourceDirectory := baseDirectory.value / "src" / "test" / "resources"

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
