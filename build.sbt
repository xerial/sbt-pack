import ReleaseTransformations._

enablePlugins(SbtTwirl)

organization := "org.xerial.sbt"
organizationName := "Xerial project"
name := "sbt-pack"
organizationHomepage := Some(new URL("http://xerial.org/"))
description := "A sbt plugin for packaging distributable Scala code"
publishMavenStyle := true
publishArtifact in Test := false

pomIncludeRepository := { _ => false }

sbtPlugin := true
crossSbtVersions := Vector("1.0.4", "0.13.16")

scalaVersion in ThisBuild := "2.12.4"

parallelExecution := true
crossPaths := false
scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked")
scriptedBufferLog := false
scriptedLaunchOpts ++= {
   import scala.collection.JavaConverters._
   management.ManagementFactory.getRuntimeMXBean().getInputArguments().asScala.filter(a => Seq("-Xmx", "-Xms").contains(a) || a.startsWith("-XX")).toSeq
}
//scalateTemplateConfig in Compile := {
//   Seq(TemplateConfig((sourceDirectory in Compile).value / "templates", Nil, Nil, Some("xerial.sbt.template")))
//}

libraryDependencies ++= Seq(
  "org.scalatra.scalate" %% "scalate-core" % "1.8.0",
  "org.apache.commons" % "commons-compress" % "1.9",
  "org.tukaani" % "xz" % "1.5",
  "org.slf4j" % "slf4j-simple" % "1.7.5",
  "org.specs2" %% "specs2-core" % "3.9.2" % "test"
)


releaseCrossBuild := true
releaseTagName := {(version in ThisBuild).value}
releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      releaseStepCommandAndRemaining("^ scripted"),
      setReleaseVersion,
      bumpVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("^ publishSigned"),
      setNextVersion,
      bumpVersion,
      commitNextVersion,
      releaseStepCommand("sonatypeReleaseAll"),
      pushChanges
    )

val bumpVersion = ReleaseStep(
   action = {state =>
     val extracted = Project extract state
      state.log.info("Bump plugin version in scripted tests")
      val command =
        sys.process.Process("./bin/bump-version.sh") #&&
          sys.process.Process("git add src/sbt-test")
      val ret = command.!
      ret match {
        case 0 => state
        case _ => state.fail
      }
    }
  )

