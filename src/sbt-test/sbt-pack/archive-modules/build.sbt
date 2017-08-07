import xerial.sbt.pack.PackPlugin._
// publish tar.gz archive to the repository (since sbt-pack-0.3.6)

val commonSettings = Defaults.coreDefaultSettings ++
  Seq(
    scalaVersion := "2.11.8",
    version := "0.1",
    crossPaths := false
  )

lazy val root = Project(
  id = "archive-modules",
  base = file("."),
  settings = commonSettings ++ publishPackArchives
).enablePlugins(PackPlugin) aggregate(module1, module2)

lazy val module1 = Project(
  id = "module1",
  base = file("module1"),
  settings = commonSettings ++ publishPackArchives ++
    Seq(
      libraryDependencies += "org.xerial" % "xerial-core" % "3.3.6"
    )
).enablePlugins(PackPlugin)

lazy val module2 = Project(
  id = "module2",
  base = file("module2"),
  settings = commonSettings ++ publishPackArchives ++
    Seq(
      libraryDependencies += "org.xerial.snappy" % "snappy-java" % "1.1.1.6"
    )
).enablePlugins(PackPlugin)
