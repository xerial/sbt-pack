import sbt._
import sbt.Keys._
import xerial.sbt.Pack._


object Build extends sbt.Build {

  val commonSettings = Defaults.defaultSettings ++
    // Add pack settings to common, so that packMain is available in subprojects' scope
    packAutoSettings ++
    Seq(
     scalaVersion := "2.10.3",
     version := "0.1",
     crossPaths := false
  )

  lazy val root = Project(
    id = "multi-module",
    base = file("."),
    settings = commonSettings ++
      Seq(
        // custom settings here
      )
  ) dependsOn(module1, module2)

  lazy val module1 = Project(
    id = "module1",
    base = file("module1"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "org.xerial" % "xerial-core" % "3.2.1"
    )
  )

  lazy val module2 = Project(
    id = "module2",
    base = file("module2"),
    settings = commonSettings ++ Seq(
      libraryDependencies += "org.xerial.snappy" % "snappy-java" % "1.1.0"
    )
  )

}
