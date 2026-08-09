ThisBuild / version      := "0.1"
ThisBuild / scalaVersion := "2.13.16"
ThisBuild / crossPaths   := false

lazy val root = (project in file("."))
  .dependsOn(module1, module2)
  .enablePlugins(PackPlugin)
  .settings(name := "nested-root")

lazy val module1 = (project in file("modules/module1"))
  .dependsOn(lib1)
  .enablePlugins(PackPlugin)
  .settings(name := "module1")

lazy val module2 = (project in file("modules/module2"))
  .dependsOn(lib2)
  .settings(name := "module2")

lazy val lib1 = (project in file("libs/lib1")).settings(name := "lib1")
lazy val lib2 = (project in file("libs/lib2")).settings(name := "lib2")
