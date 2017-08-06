import com.mojolly.scalate.ScalatePlugin.ScalateKeys.scalateTemplateConfig
import ReleaseTransformations._

scriptedSettings
scalateSettings

organization := "org.xerial.sbt"
organizationName := "Xerial project"
name := "sbt-pack"
organizationHomepage := Some(new URL("http://xerial.org/"))
description := "A sbt plugin for packaging distributable Scala code"
publishMavenStyle := true
publishArtifact in Test := false

pomIncludeRepository := { _ => false }

sbtPlugin := true
crossSbtVersions := Vector("1.0.0-RC3", "0.13.16")

parallelExecution := true
crossPaths := false
scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-target:jvm-1.6")
scriptedBufferLog := false
scriptedLaunchOpts ++= {
   import scala.collection.JavaConverters._
   management.ManagementFactory.getRuntimeMXBean().getInputArguments().asScala.filter(a => Seq("-Xmx", "-Xms").contains(a) || a.startsWith("-XX")).toSeq
}
scalateTemplateConfig in Compile := {
   Seq(TemplateConfig((sourceDirectory in Compile).value / "templates", Nil, Nil, Some("xerial.sbt.template")))
}

libraryDependencies ++= Seq(
  "org.fusesource.scalate" % "scalate-core_2.10" % "1.6.1",
  "org.apache.commons" % "commons-compress" % "1.9",
  "org.tukaani" % "xz" % "1.5",
  "org.slf4j" % "slf4j-simple" % "1.7.5",
  "org.specs2" %% "specs2" % "2.4.1" % "test"
)

releaseTagName := {(version in ThisBuild).value}

releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      ReleaseStep(
        action = {state =>
          val extracted = Project extract state
          extracted.runAggregated(scriptedTests in Global in extracted.get(thisProjectRef), state)
        }
      ),
      setReleaseVersion,
      bumpVersion,
      commitReleaseVersion,
      tagRelease,
      ReleaseStep(action = Command.process("publishSigned", _)),
      setNextVersion,
      bumpVersion,
      commitNextVersion,
      ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
      pushChanges
    )

val bumpVersion = ReleaseStep(
   action = {state =>
     val extracted = Project extract state
      state.log.info("Bump plugin version in scripted tests")
      val command =
        Process("./bin/bump-version.sh") #&&
          Process("git add src/sbt-test")
      val ret = command.!
      ret match {
        case 0 => state
        case _ => state.fail
      }
    }
  )

