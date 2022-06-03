enablePlugins(PackPlugin)

scalaVersion := "2.12.15"

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))
