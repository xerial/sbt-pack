/*
 * Copyright 2012 Taro L. Saito
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xerial.sbt

import sbt._
import Keys._
import sbt.ScriptedPlugin._
import net.virtualvoid.sbt.graph.Plugin._

import sbtrelease._
import sbtrelease.ReleasePlugin._
import sbtrelease.ReleaseStep
import ReleaseStateTransformations._

import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._

object PackBuild extends Build {

  val SCALA_VERSION = "2.10.5"

  lazy val buildSettings = Defaults.coreDefaultSettings ++ releaseSettings ++ scriptedSettings ++ graphSettings ++ scalateSettings ++ Seq[Setting[_]](
    organization := "org.xerial.sbt",
    organizationName := "Xerial project",
    organizationHomepage := Some(new URL("http://xerial.org/")),
    description := "A sbt plugin for packaging distributable Scala code",
    scalaVersion := SCALA_VERSION,
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := {
      _ => false
    },
    sbtPlugin := true,
    parallelExecution := true,
    crossPaths := false,
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-target:jvm-1.6"),
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= {
      import scala.collection.JavaConverters._
      management.ManagementFactory.getRuntimeMXBean().getInputArguments().asScala.filter(a => Seq("-Xmx","-Xms").contains(a) || a.startsWith("-XX")).toSeq
    },
    scalateTemplateConfig in Compile <<= (sourceDirectory in Compile) { base =>
      Seq(TemplateConfig(base / "templates", Nil, Nil, Some("xerial.sbt.template")))
    },
    ReleaseKeys.tagName := { (version in ThisBuild).value },
    ReleaseKeys.releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      ReleaseStep(
        action = { state =>
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
  )

  val bumpVersion = ReleaseStep(
    action = { state =>
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

  // Project modules
  lazy val sbtPack = Project(
    id = "sbt-pack",
    base = file("."),
    settings = buildSettings ++
      Seq(libraryDependencies ++=
        Seq(
          "org.fusesource.scalate" % "scalate-core_2.10" % "1.6.1",
          "org.apache.commons" % "commons-compress" % "1.9",
          "org.tukaani" % "xz" % "1.5",
          "org.slf4j" % "slf4j-simple" % "1.7.5",
          "org.specs2" %% "specs2" % "2.4.1" % "test"
        )
      )
  )

}








