enablePlugins(PackPlugin)

scalaVersion := "2.11.8"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))



