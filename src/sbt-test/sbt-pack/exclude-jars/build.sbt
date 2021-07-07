enablePlugins(PackPlugin)
scalaVersion := "2.12.13"
crossPaths := false

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

packExcludeJars := Seq("scala-library.*\\.jar", "scala-reflect.*\\.jar")
