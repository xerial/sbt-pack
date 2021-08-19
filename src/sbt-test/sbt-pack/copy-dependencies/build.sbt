ThisBuild / scalaVersion := "2.12.14"

val commonSettings = Seq(
  scalaVersion               := "2.12.14",
  version                    := "0.1",
  crossPaths                 := false,
  packCopyDependenciesTarget := target.value / "WEB-INF/lib"
)

lazy val root =
  (project in file("."))
    .settings(commonSettings)
    .enablePlugins(PackPlugin)
    .dependsOn(module1, module2, module3, module4)

lazy val module1 =
  project
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        "org.xerial"     % "xerial-core"    % "3.3.6",
        "org.slf4j"      % "slf4j-api"      % "1.7.2" force (),
        "jakarta-regexp" % "jakarta-regexp" % "1.4",
        "xalan"          % "xalan"          % "2.7.1"
      ),
      packCopyDependenciesUseSymbolicLinks := false
    )
    .enablePlugins(PackPlugin)

lazy val module2 =
  project
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        "org.xerial.snappy" % "snappy-java" % "1.1.1.6",
        "org.slf4j"         % "slf4j-api"   % "1.7.6"
      )
    )
    .enablePlugins(PackPlugin)

lazy val module3 =
  project
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        "commons-digester"    % "commons-digester"    % "2.1",
        "commons-collections" % "commons-collections" % "3.2.1"
      )
    )
    .enablePlugins(PackPlugin)

lazy val module4 =
  project
    .dependsOn(module2)
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        "commons-digester" % "commons-digester" % "2.1"
          exclude ("commons-beanutils", "commons-beanutils"),
        "commons-collections" % "commons-collections" % "3.2.1"
      )
    )
    .enablePlugins(PackPlugin)
