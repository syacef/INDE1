name := "srvc-store"
version := "0.1.0"
scalaVersion := "2.13.10"

Compile / scalaSource := baseDirectory.value / "src" / "main" / "scala"
Compile / resourceDirectory := baseDirectory.value / "src" / "main" / "resources"
Test / scalaSource := baseDirectory.value / "src" / "test" / "scala"
Test / resourceDirectory := baseDirectory.value / "src" / "test" / "resources"

target := baseDirectory.value / "target"

libraryDependencies ++= Seq(
    "com.typesafe" % "config" % "1.4.2",

    "ch.qos.logback" % "logback-classic" % "1.4.11",

    "org.scalatest" %% "scalatest" % "3.2.17" % Test
)
