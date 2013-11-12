import sbt._
import sbt.Keys._
import xerial.sbt.Pack._


object Build extends sbt.Build {

  val commonSettings = Defaults.defaultSettings ++ packSettings ++ publishPackArchive ++ Seq(
     scalaVersion := "2.10.3",
     version := "0.1",
     crossPaths := false
  )

  object ProgMap {
    val m1 = Map("m1" -> "sample.Module1")
    val m2 = Map("m2" -> "sample.Module2")
  }

//  def addArchive: SettingsDefinition =
//  {
//    val pkgd = packagedArtifacts := packagedArtifacts.value updated (Artifact(name.value, "arch", "tar.gz"), packArchive.value)
//    seq( artifacts += a, pkgd )
//  }

  lazy val root = Project(
    id = "archive-modules",
    base = file("."),
    settings = commonSettings
      ++ Seq(
      packMain := ProgMap.m1 ++ ProgMap.m2
    )
  ) aggregate(module1, module2)

  lazy val module1 = Project(
    id = "module1",
    base = file("module1"),
    settings = commonSettings
      ++ Seq(
      packMain := ProgMap.m1,
      libraryDependencies += "org.xerial" % "xerial-core" % "3.2.1"
    )
  )

  lazy val module2 = Project(
    id = "module2",
    base = file("module2"),
    settings = commonSettings
      ++ Seq(
      packMain := ProgMap.m2,
      libraryDependencies += "org.xerial.snappy" % "snappy-java" % "1.1.0"
    )
  )

}
