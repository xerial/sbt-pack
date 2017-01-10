import sbt._
import sbt.Keys._
import xerial.sbt.Pack._

object Build extends sbt.Build {

  lazy val root = Project(
    id = "min-project",
    base = file("."),
    settings = Defaults.defaultSettings ++ packAutoSettings ++
      Seq(
        scalaVersion := "2.11.8",
        // custom settings here
        crossPaths := false
      )
  )
}
