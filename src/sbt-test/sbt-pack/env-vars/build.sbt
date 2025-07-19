import xerial.sbt.pack.PackPlugin

enablePlugins(PackPlugin)
name := "env-vars"

scalaVersion := "2.12.20"
// custom settings here
crossPaths := false

packEnvVars := Map(
  "test" -> Map("key1" -> "value1", "key2" -> "value2")
)
