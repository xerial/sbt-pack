import xerial.sbt.pack.PackPlugin

enablePlugins(PackPlugin)

scalaVersion := "2.12.20"

libraryDependencies := Seq("org.xerial" % "xerial-core" % "3.3.6")

packJarNameConvention := "no-version"
