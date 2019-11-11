addSbtPlugin("com.jsuereth"      % "sbt-pgp"      % "1.1.2")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "3.8")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % "2.2.1")
addSbtPlugin("com.typesafe.sbt"  % "sbt-twirl"    % "1.3.16")
addSbtPlugin("com.dwijnand"      % "sbt-dynver"   % "4.0.0")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
