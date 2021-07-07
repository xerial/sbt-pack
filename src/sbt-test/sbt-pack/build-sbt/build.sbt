enablePlugins(PackPlugin)

scalaVersion := "2.12.13"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
