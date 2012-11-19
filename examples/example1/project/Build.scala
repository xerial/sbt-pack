import sbt._
import sbt.Keys._
import xerial.sbt.Pack._

object Build extends sbt.Build {

  lazy val root = Project(
    id = "example1",
    base = file("."),
    settings = Defaults.defaultSettings ++ packSettings ++
      Seq(
        // Map from program name -> Main class
        packMain := Map("hello" -> "Hello"),
        // custom settings here
        crossPaths := false
      )
  )
}