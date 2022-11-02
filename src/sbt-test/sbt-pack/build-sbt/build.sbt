enablePlugins(PackPlugin)

scalaVersion := "2.12.17"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
