import sbt._
import sbt.Keys._
import xerial.sbt.Pack._


object Build extends sbt.Build {

  val commonSettings = Defaults.defaultSettings ++ Seq(
     scalaVersion := "2.10.2",
     version := "0.1",
     crossPaths := false
  )

  lazy val root = Project(
    id = "multi-module",
    base = file("."),
    settings = commonSettings ++ packSettings ++
      Seq(
        packMain := Map("m1" -> "sample.Module1", "m2" -> "sample.Module2")
        // custom settings here
      )
  ) dependsOn(module1, module2)

  lazy val module1 = Project(
    id = "module1",
    base = file("module1"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "org.xerial" % "xerial-core" % "3.2.0"
    )
  )

  lazy val module2 = Project(
    id = "module2",
    base = file("module2"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "org.xerial.snappy" % "snappy-java" % "1.1.0-M1"
    )
  )

}