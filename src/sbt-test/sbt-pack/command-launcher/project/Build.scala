import sbt._
import sbt.Keys._
import xerial.sbt.Pack._

object Build extends sbt.Build {

  lazy val root = Project(
    id = "command-launcher",
    base = file("."),
    settings = Defaults.defaultSettings ++ packSettings ++
      Seq(
        scalaVersion := "2.11.6",
        scalacOptions ++= Seq("-deprecation", "-feature"),
        // Mapping from program name -> Main class
        packMain := Map("launcher" -> "launcher.Main"),
        // Add custom settings here
        crossPaths := false,
        libraryDependencies ++= Seq(
          // include both jar and source.jar
          "org.xerial" % "xerial-lens" % "3.3.6",
          "org.xerial" % "xerial-core" % "3.3.6"
        )
      )
  )
}