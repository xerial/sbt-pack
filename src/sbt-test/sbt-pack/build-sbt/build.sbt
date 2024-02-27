enablePlugins(PackPlugin)

scalaVersion := "2.12.19"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
