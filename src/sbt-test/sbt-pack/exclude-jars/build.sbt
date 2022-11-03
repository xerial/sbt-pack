enablePlugins(PackPlugin)
version      := "0.1"
scalaVersion := "2.13.10"
crossPaths   := false
name         := "exclude-jars"
libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

packExcludeJars := Seq("scala-library.*\\.jar", "scala-reflect.*\\.jar")
