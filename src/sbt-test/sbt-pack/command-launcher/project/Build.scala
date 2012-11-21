import sbt._
import sbt.Keys._
import xerial.sbt.Pack._

object Build extends sbt.Build {

  lazy val root = Project(
    id = "command-launcher",
    base = file("."),
    settings = Defaults.defaultSettings ++ packSettings ++
      Seq(
        // Mapping from program name -> Main class
        packMain := Map("launcher" -> "launcher.Main"),
        // Add custom settings here
        crossPaths := false,
        resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
        libraryDependencies += "org.xerial" % "xerial-lens" % "3.1-SNAPSHOT"
      )
  )
}