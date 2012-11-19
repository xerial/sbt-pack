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

import java.io.File
import sbt._
import Keys._

import sbtrelease.ReleasePlugin._
import com.jsuereth.pgp.sbtplugin.PgpPlugin._

object SbtPackBuild extends Build {

  val SCALA_VERSION = "2.9.2"

  def releaseResolver(v: String): Resolver = {
    val profile = System.getProperty("xerial.profile", "default")
    profile match {
      case "default" => {
        val nexus = "https://oss.sonatype.org/"
        if (v.trim.endsWith("SNAPSHOT"))
          "snapshots" at nexus + "content/repositories/snapshots"
        else
          "releases" at nexus + "service/local/staging/deploy/maven2"
      }
      case p => {
        sys.error("unknown xerial.profile:%s".format(p))
      }
    }
  }

  lazy val buildSettings = Defaults.defaultSettings ++ releaseSettings ++ Seq[Setting[_]](
    organization := "org.xerial.sbt",
    organizationName := "Xerial project",
    organizationHomepage := Some(new URL("http://xerial.org/")),
    description := "A sbt plugin for packaging distributable Scala code",
    scalaVersion := SCALA_VERSION,
    publishMavenStyle := true,
    publishArtifact in Test := false,
    publishTo <<= version { (v) => Some(releaseResolver(v)) },
    pomIncludeRepository := {
      _ => false
    },
    sbtPlugin := true,
    parallelExecution := true,
    crossPaths := false,
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-target:jvm-1.5"),
    pomExtra := {
      <url>http://xerial.org/</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
      </licenses>
        <scm>
          <connection>scm:git:github.com/xerial/sbt-pack.git</connection>
          <developerConnection>scm:git:git@github.com:xerial/sbt-pack.git</developerConnection>
          <url>github.com/xerial/sbt-pack.git</url>
        </scm>
        <properties>
          <scala.version>
            {SCALA_VERSION}
          </scala.version>
          <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        </properties>
        <developers>
          <developer>
            <id>leo</id>
            <name>Taro L. Saito</name>
            <url>http://xerial.org/leo</url>
          </developer>
        </developers>
    },
    useGpg := true,
    useGpgAgent := false
  )


  // Project modules
  lazy val sbtPack = Project(
    id = "sbt-pack",
    base = file("."),
    settings = buildSettings ++
      Seq(libraryDependencies ++=
        Seq(
          "org.scalatest" %% "scalatest" % "2.0.M5" % "test",
          "org.codehaus.plexus" % "plexus-classworlds" % "2.4" % "provided",
          "org.xerial" % "xerial-core" % "3.0"
        )
      )
  )

}








