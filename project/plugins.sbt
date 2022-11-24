ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

addSbtPlugin("com.github.sbt"    % "sbt-pgp"      % "2.2.0")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "3.9.15")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % "2.3.4")
addSbtPlugin("com.typesafe.play" % "sbt-twirl"    % "1.6.0-M7")
addSbtPlugin("com.dwijnand"      % "sbt-dynver"   % "4.1.1")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % "2.5.0")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
