enablePlugins(PackPlugin)
scalaVersion := "2.12.15"
crossPaths   := false
name         := "exclude-makefile"
version      := "0.1"
libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

packExcludeJars := Seq("scala-library.*\\.jar", "scala-reflect.*\\.jar")

packGenerateMakefile := false
