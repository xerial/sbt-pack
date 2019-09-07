addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "2.3")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"      % "1.1.1")
addSbtPlugin("com.typesafe.sbt"  % "sbt-twirl"    % "1.3.13")
addSbtPlugin("com.geirsson"      % "sbt-scalafmt" % "1.4.0")
addSbtPlugin("com.dwijnand"      % "sbt-dynver"   % "3.3.0")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
