enablePlugins(PackPlugin)

scalaVersion := "2.13.13"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
