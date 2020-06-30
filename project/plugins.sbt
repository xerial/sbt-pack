addSbtPlugin("com.jsuereth"      % "sbt-pgp"      % "2.0.1")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "3.9.2")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % "2.3.3")
addSbtPlugin("com.typesafe.sbt"  % "sbt-twirl"    % "1.5.0")
addSbtPlugin("com.dwijnand"      % "sbt-dynver"   % "4.1.1")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
