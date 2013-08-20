import sbt._
import sbt.Keys._
import xerial.sbt.Pack._

object Build extends sbt.Build {

  lazy val root = Project(
    id = "multi-module",
    base = file("."),
    settings = Defaults.defaultSettings ++ packSettings ++
      Seq(
        scalaVersion := "2.10.1",
        // custom settings here
        crossPaths := false
      )
  ) dependsOn(module1, module2)

  lazy val module1 = Project(
    id = "module1",
    base = file("module1"),
    settings = Defaults.defaultSettings ++ Seq(
      libraryDependencies += "org.xerial" % "xerial-core" % "3.2.0"
    )
  )

  lazy val module2 = Project(
    id = "module2",
    base = file("module2"),
    settings = Defaults.defaultSettings ++ packSettings ++ Seq(
      libraryDependencies += "org.xerial.snappy" % "snappy-java" % "1.1.0-M1"
    )
  )

}