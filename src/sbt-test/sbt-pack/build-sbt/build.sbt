enablePlugins(PackPlugin)

scalaVersion := "2.13.16"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
