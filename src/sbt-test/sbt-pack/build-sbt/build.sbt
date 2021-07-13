enablePlugins(PackPlugin)

scalaVersion := "2.13.6"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
