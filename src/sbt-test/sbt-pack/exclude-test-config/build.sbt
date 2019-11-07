scalaVersion in ThisBuild := "2.12.6"

val commonSettings = Seq(
  scalaVersion := "2.12.6",
  version := "0.1",
  crossPaths := false
)

lazy val module1 =
  project
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        "org.slf4j"         % "slf4j-api"   % "1.7.6"
      ),
    )
    .enablePlugins(PackPlugin)

lazy val module2 =
  project
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        "org.xerial.snappy" % "snappy-java" % "1.1.1.6"
      )
    )
    .enablePlugins(PackPlugin)

lazy val client =
  project
    .settings(commonSettings)
    .dependsOn(module1, module2 % "test->test")
    .enablePlugins(PackPlugin)

lazy val server =
  project
    .dependsOn(module2)
    .settings(commonSettings)
    .dependsOn(module1, module2 % "compile->compile;test->test")
    .enablePlugins(PackPlugin)
