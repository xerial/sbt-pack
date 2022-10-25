import xerial.sbt.pack.PackPlugin._
// publish tar.gz archive to the repository (since sbt-pack-0.3.6)

val commonSettings = Defaults.coreDefaultSettings ++
  Seq(
    scalaVersion := "2.12.17",
    version      := "0.1",
    crossPaths   := false
  )

lazy val root =
  Project(
    id = "archive-modules",
    base = file(".")
  ).enablePlugins(PackPlugin)
    .settings(commonSettings)
    .settings(publishPackArchives)
    .aggregate(module1, module2) // dependency of module2 should not be included
    .dependsOn(module1)

lazy val module1 = Project(
  id = "module1",
  base = file("module1")
).enablePlugins(PackPlugin)
  .settings(commonSettings)
  .settings(publishPackArchives)
  .settings(
    libraryDependencies += "org.xerial" % "xerial-core" % "3.3.6"
  )

lazy val module2 = Project(
  id = "module2",
  base = file("module2")
).enablePlugins(PackPlugin)
  .settings(commonSettings)
  .settings(publishPackArchives)
  .settings(
    libraryDependencies += "org.xerial.snappy" % "snappy-java" % "1.1.1.6"
  )

// Empty archive stem test
lazy val module3 = Project(
  id = "module3",
  base = file("module3")
).enablePlugins(PackPlugin)
  .settings(commonSettings)
  .settings(publishPackArchives)
  .settings(
    libraryDependencies += "org.xerial.snappy" % "snappy-java" % "1.1.1.6",
    packArchiveStem                           := ""
  )
