enablePlugins(PackPlugin)

scalaVersion := "2.12.16"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
