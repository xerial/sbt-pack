addSbtPlugin("com.jsuereth"      % "sbt-pgp"      % "2.0.1")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "3.8.1")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % "2.3.0")
addSbtPlugin("com.typesafe.sbt"  % "sbt-twirl"    % "1.5.0")
addSbtPlugin("com.dwijnand"      % "sbt-dynver"   % "4.0.0")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
