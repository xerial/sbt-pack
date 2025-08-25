enablePlugins(PackPlugin)

name := "log-level-test"

version := "0.1.0"

scalaVersion := "2.13.15"

// Test dependencies
libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % scalaVersion.value
)

packMain := Map("hello" -> "HelloWorld")

// Test that logLevel setting is respected
// When set to Warn, info messages should not appear
Test / logLevel := Level.Warn