import xerial.sbt.pack.PackPlugin

enablePlugins(PackPlugin)

scalaVersion := "2.12.20"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
