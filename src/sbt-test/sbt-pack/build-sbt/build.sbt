enablePlugins(PackPlugin)

scalaVersion := "2.12.18"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
