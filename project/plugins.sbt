addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "3.6")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"      % "1.1.1")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % "2.0.0")
addSbtPlugin("com.typesafe.sbt"  % "sbt-twirl"    % "1.3.15")
addSbtPlugin("com.dwijnand"      % "sbt-dynver"   % "3.3.0")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
