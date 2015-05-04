
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "0.5.0")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8.5")

addSbtPlugin("com.mojolly.scalate" % "xsbt-scalate-generator" % "0.4.2")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")

libraryDependencies <+= sbtVersion("org.scala-sbt" % "scripted-plugin" % _)
