Global / onChangedBuildSource := ReloadOnSourceChanges

enablePlugins(SbtTwirl)
enablePlugins(SbtPlugin)

val SCALA_3 = "3.7.4"
val SCALA_2 = "2.12.20"
ThisBuild / crossScalaVersions := List(SCALA_3, SCALA_2)

pluginCrossBuild / sbtVersion := {
  scalaBinaryVersion.value match {
    case "2.12" =>
      (pluginCrossBuild / sbtVersion).value
    case _ =>
      "2.0.0-M4"
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
crossPaths        := true
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
  "org.wvlet.airframe" %% "airspec"          % "2025.1.19" % Test,
  "org.apache.commons"  % "commons-compress" % "1.27.1",
  "org.tukaani"         % "xz"               % "1.11"
)

// Publishing settings
homepage := Some(url("https://github.com/xerial/sbt-pack"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/xerial/sbt-pack"),
    "scm:git@github.com:xerial/sbt-pack.git"
  )
)
developers := List(
  Developer(id = "leo", name = "Taro L. Saito", email = "leo@xerial.org", url = url("http://xerial.org/leo"))
)
licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

// Sonatype publishing
publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}
