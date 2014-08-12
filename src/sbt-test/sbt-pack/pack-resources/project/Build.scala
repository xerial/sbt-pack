import sbt._
import sbt.Keys._
import xerial.sbt.Pack._

object Build extends sbt.Build {

  lazy val root = Project(
    id = "pack-resources",
    base = file("."),
    settings = Defaults.defaultSettings ++ packAutoSettings ++
      Seq(
        scalaVersion := "2.10.3",
        // Copy files from ${root}/web/... to ${root}/target/pack/web-content...
        packResourceDir += (baseDirectory.value / "web" -> "web-content"),
        // custom settings here
        crossPaths := false
      )
  )
}
