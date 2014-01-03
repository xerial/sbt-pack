packSettings

scalaVersion := "2.10.3"

packMain := Map("hello" -> "example.Hello")

packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/extra"))



