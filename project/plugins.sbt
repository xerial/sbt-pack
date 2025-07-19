ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

addSbtPlugin("com.github.sbt"          % "sbt-pgp"      % "2.3.1")
addSbtPlugin("org.scalameta"           % "sbt-scalafmt" % "2.3.4")
addSbtPlugin("org.playframework.twirl" % "sbt-twirl"    % "2.0.9")
addSbtPlugin("com.github.sbt"          % "sbt-dynver"   % "5.1.1")
addSbtPlugin("org.scalameta"           % "sbt-scalafmt" % "2.5.5")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
