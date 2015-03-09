import sbt._
import sbt.Keys._
import xerial.sbt.Pack._


object Build extends sbt.Build {

  val commonSettings = Defaults.defaultSettings ++
    // Add pack, pack-archive commands
    packAutoSettings ++
    // publish tar.gz archive to the repository (since sbt-pack-0.3.6)
    publishPackArchives ++
    Seq(
     scalaVersion := "2.11.6",
     version := "0.1",
     crossPaths := false
  )

  lazy val root = Project(
    id = "archive-modules",
    base = file("."),
    settings = commonSettings
  ) aggregate(module1, module2)

  lazy val module1 = Project(
    id = "module1",
    base = file("module1"),
    settings = commonSettings ++
      Seq(
        libraryDependencies += "org.xerial" % "xerial-core" % "3.3.6"
      )
  )

  lazy val module2 = Project(
    id = "module2",
    base = file("module2"),
    settings = commonSettings ++
      Seq(
        libraryDependencies += "org.xerial.snappy" % "snappy-java" % "1.1.1.6"
      )
  )

}
