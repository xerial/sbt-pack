addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "2.0")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"      % "1.1.0")
addSbtPlugin("com.github.gseitz" % "sbt-release"  % "1.0.7")
addSbtPlugin("com.typesafe.sbt"  % "sbt-twirl"    % "1.3.13")
addSbtPlugin("com.geirsson"      % "sbt-scalafmt" % "1.4.0")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
