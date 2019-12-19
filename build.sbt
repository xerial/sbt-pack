enablePlugins(SbtTwirl)
enablePlugins(SbtPlugin)

organization := "org.xerial.sbt"
organizationName := "Xerial project"
name := "sbt-pack"
organizationHomepage := Some(new URL("http://xerial.org/"))
description := "A sbt plugin for packaging distributable Scala code"

publishMavenStyle := true
publishArtifact in Test := false

pomIncludeRepository := { _ =>
  false
}

crossSbtVersions := Vector("1.2.8", "0.13.17")

scalaVersion in ThisBuild := "2.12.8"

parallelExecution := true
crossPaths := false
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
  "org.slf4j"            % "slf4j-simple"     % "1.7.30",
  "org.apache.commons"   % "commons-compress" % "1.19",
  "org.scalatra.scalate" %% "scalate-core"    % "1.9.5",
  "org.tukaani"          % "xz"               % "1.8",
  "org.specs2"           %% "specs2-core"     % "4.8.1" % "test"
)
