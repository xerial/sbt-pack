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

testFrameworks += new TestFramework("wvlet.airspec.Framework")

libraryDependencies ++= Seq(
  "org.wvlet.airframe" %% "airspec"          % "23.2.0" % Test,
  "org.apache.commons"  % "commons-compress" % "1.22",
  "org.tukaani"         % "xz"               % "1.9"
)
