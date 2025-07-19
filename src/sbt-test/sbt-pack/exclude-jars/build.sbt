import xerial.sbt.pack.PackPlugin

enablePlugins(PackPlugin)
version      := "0.1"
scalaVersion := "2.12.20"
crossPaths   := false
name         := "exclude-jars"
libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

packExcludeJars := Seq("scala-library.*\\.jar", "scala-reflect.*\\.jar")
