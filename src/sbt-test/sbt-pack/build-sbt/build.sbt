enablePlugins(PackPlugin)

scalaVersion := "2.12.6"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
