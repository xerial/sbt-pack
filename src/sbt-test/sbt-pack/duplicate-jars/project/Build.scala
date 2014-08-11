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
    id = "duplicate-jars",
    base = file("."),
    settings = commonSettings ++
      Seq(
        packMain := (packMain in module1).value ++ (packMain in module2).value
        // custom settings here
      )
  ) dependsOn(module1, module2)

  lazy val module1 = Project(
    id = "module1",
    base = file("module1"),
    settings = commonSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.xerial" % "xerial-core" % "3.2.1",
        "org.slf4j" % "slf4j-api" % "1.7.2" force()
      )
    )
  )

  lazy val module2 = Project(
    id = "module2",
    base = file("module2"),
    settings = commonSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.xerial.snappy" % "snappy-java" % "1.1.0",
        "org.slf4j" % "slf4j-api" % "1.7.6"
      )
    )
  )

}
