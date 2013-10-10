import sbt._
import sbt.Keys._
import xerial.sbt.Pack._

object Build extends sbt.Build {

  lazy val root = Project(
    id = "command-launcher",
    base = file("."),
    settings = Defaults.defaultSettings ++ packSettings ++
      Seq(
        scalaVersion := "2.10.3",
        scalacOptions ++= Seq("-deprecation", "-feature"),
        // Mapping from program name -> Main class
        packMain := Map("launcher" -> "launcher.Main"),
        // Add custom settings here
        crossPaths := false,
        libraryDependencies ++= Seq(
          // include both jar and source.jar
          "org.xerial" % "xerial-lens" % "3.2.1" jar() classifier("sources"),
          "org.xerial" % "xerial-core" % "3.2.1"
        )
      )
  )
}