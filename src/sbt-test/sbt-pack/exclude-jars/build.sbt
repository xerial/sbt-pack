packAutoSettings
scalaVersion := "2.10.5"
crossPaths := false

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "1.4.0" % "provided",
  "com.typesafe.play" %% "play-json" % "2.3.9"
)

packExcludeJars := Seq("scala-library-.*\\.jar", "scala-reflect-.*\\.jar")
