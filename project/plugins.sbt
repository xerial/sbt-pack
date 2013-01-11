
addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.1.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.6")

libraryDependencies <+= sbtVersion("org.scala-sbt" % "scripted-plugin" % _)
