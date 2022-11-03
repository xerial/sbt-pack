enablePlugins(PackPlugin)

scalaVersion := "2.13.10"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
