import sbt._
import sbt.Keys._
import xerial.sbt.PackPlugin._


object Build extends sbt.Build {

  val commonSettings = Defaults.coreDefaultSettings ++ Seq(
     scalaVersion := "2.11.8",
     version := "0.1",
     crossPaths := false
  )

  lazy val root = Project(
    id = "multi-module",
    base = file("."),
    settings = commonSettings ++ packAutoSettings ++
      Seq(
        // custom settings here
      )
  ) dependsOn(module1, module2)

  lazy val module1 = Project(
    id = "module1",
    base = file("module1"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "org.xerial" % "xerial-core" % "3.3.6"
    )
  )

  lazy val module2 = Project(
    id = "module2",
    base = file("module2"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "org.xerial.snappy" % "snappy-java" % "1.1.1.6"
    )
  )

}
