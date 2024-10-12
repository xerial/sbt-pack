ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

addSbtPlugin("com.github.sbt"          % "sbt-pgp"      % "2.3.0")
addSbtPlugin("org.xerial.sbt"          % "sbt-sonatype" % "3.12.2")
addSbtPlugin("org.scalameta"           % "sbt-scalafmt" % "2.3.4")
addSbtPlugin("org.playframework.twirl" % "sbt-twirl"    % "2.0.6")
addSbtPlugin("com.github.sbt"          % "sbt-dynver"   % "5.1.0")
addSbtPlugin("org.scalameta"           % "sbt-scalafmt" % "2.5.2")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
