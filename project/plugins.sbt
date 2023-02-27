ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

addSbtPlugin("com.github.sbt"    % "sbt-pgp"      % "2.2.1")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "3.9.17")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % "2.3.4")
addSbtPlugin("com.typesafe.play" % "sbt-twirl"    % "1.6.0-RC2")
addSbtPlugin("com.dwijnand"      % "sbt-dynver"   % "4.1.1")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % "2.5.0")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
