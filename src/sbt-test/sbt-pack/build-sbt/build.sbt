enablePlugins(PackPlugin)

scalaVersion := "2.12.4"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
