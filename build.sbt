Global / onChangedBuildSource := ReloadOnSourceChanges

enablePlugins(SbtTwirl)
enablePlugins(SbtPlugin)

crossScalaVersions += "3.3.4"

pluginCrossBuild / sbtVersion := {
  scalaBinaryVersion.value match {
    case "2.12" =>
      (pluginCrossBuild / sbtVersion).value
    case _ =>
      "2.0.0-M2"
  }
}

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
  "org.wvlet.airframe" %% "airspec"          % "2025.1.14" % Test,
  "org.apache.commons"  % "commons-compress" % "1.27.1",
  "org.tukaani"         % "xz"               % "1.10"
)
