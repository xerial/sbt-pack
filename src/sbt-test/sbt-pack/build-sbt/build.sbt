enablePlugins(PackPlugin)

scalaVersion := "2.13.8"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
