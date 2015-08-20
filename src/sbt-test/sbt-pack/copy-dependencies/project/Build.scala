import sbt._
import sbt.Keys._
import xerial.sbt.Pack._


object Build extends sbt.Build {
  val commonSettings = packSettings ++ Seq(
    scalaVersion := "2.11.6",
    version := "0.1",
    crossPaths := false,
    packCopyDependenciesTarget := target.value / "WEB-INF/lib"
  )

  lazy val root = (project in file(".")).settings(
    commonSettings ++
      Seq(
        // custom settings here
      ): _*
  ) dependsOn(module1, module2)

  lazy val module1 = project.settings(
    commonSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.xerial" % "xerial-core" % "3.3.6",
        "org.slf4j" % "slf4j-api" % "1.7.2" force(),
        "jakarta-regexp" % "jakarta-regexp" % "1.4",
        "xalan" % "xalan" % "2.7.1"
      ),
      packCopyDependenciesUseSymbolicLinks := false
    ): _*
  )

  lazy val module2 = project.settings(
    commonSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.xerial.snappy" % "snappy-java" % "1.1.1.6",
        "org.slf4j" % "slf4j-api" % "1.7.6"
      )
    ): _*
  )

  lazy val module3 = project.settings(
    commonSettings ++ Seq(
      libraryDependencies ++= Seq(
        "commons-digester" % "commons-digester" % "2.1",
        "commons-collections" % "commons-collections" % "3.2.1"
      )
    ): _*
  )

  lazy val module4 = project.dependsOn(module2).settings(
    commonSettings ++ Seq(
      libraryDependencies ++= Seq(
        "commons-digester" % "commons-digester" % "2.1"
          exclude("commons-beanutils", "commons-beanutils"),
        "commons-collections" % "commons-collections" % "3.2.1"
      )
    ): _*
  )
}
