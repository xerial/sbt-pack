addSbtPlugin("com.github.sbt"   % "sbt-pgp"      % "2.1.2")
addSbtPlugin("org.xerial.sbt"   % "sbt-sonatype" % "3.9.13")
addSbtPlugin("org.scalameta"    % "sbt-scalafmt" % "2.3.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl"    % "1.5.1")
addSbtPlugin("com.dwijnand"     % "sbt-dynver"   % "4.1.1")
addSbtPlugin("org.scalameta"    % "sbt-scalafmt" % "2.4.6")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
