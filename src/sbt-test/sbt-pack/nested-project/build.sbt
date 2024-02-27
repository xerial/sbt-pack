name := "nested"

ThisBuild / version := "0.1"

ThisBuild / scalaVersion := "2.12.19"

enablePlugins(PackPlugin)

lazy val root = (project in file("."))
  .dependsOn(module1, module2)
  .enablePlugins(PackPlugin)

lazy val module1 = (project in file("modules/module1")).dependsOn(lib1).enablePlugins(PackPlugin)
lazy val module2 = (project in file("modules/module2")).dependsOn(lib2)

lazy val lib1 = project in file("libs/lib1")
lazy val lib2 = project in file("libs/lib2")
