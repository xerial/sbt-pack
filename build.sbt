Global / onChangedBuildSource := ReloadOnSourceChanges

enablePlugins(SbtTwirl)
enablePlugins(SbtPlugin)

organization         := "org.xerial.sbt"
organizationName     := "Xerial project"
name                 := "sbt-pack"
organizationHomepage := Some(new URL("http://xerial.org/"))
description          := "A sbt plugin for packaging distributable Scala code"

publishMavenStyle      := true
Test / publishArtifact := false

pomIncludeRepository := { _ =>
  false
}

ThisBuild / scalaVersion := "2.12.17"

parallelExecution := true
crossPaths        := false
scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked")

scriptedBufferLog := false
scriptedLaunchOpts ++= {
  import scala.collection.JavaConverters._
  management.ManagementFactory
    .getRuntimeMXBean()
    .getInputArguments().asScala
    .filter(a => Seq("-Xmx", "-Xms").contains(a) || a.startsWith("-XX")).toSeq ++
    Seq("-Dplugin.version=" + version.value)
}

libraryDependencies ++= Seq(
  "org.slf4j"          % "slf4j-api"        % "1.7.36",
  "org.apache.commons" % "commons-compress" % "1.21",
  "org.tukaani"        % "xz"               % "1.9",
  "org.specs2"        %% "specs2-core"      % "4.18.0" % "test"
)
