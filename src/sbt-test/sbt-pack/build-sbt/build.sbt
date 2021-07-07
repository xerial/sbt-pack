enablePlugins(PackPlugin)

scalaVersion := "2.12.14"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
