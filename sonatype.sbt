sonatypeProfileName := "org.xerial"

pomExtra := {
  <url>http://xerial.org/</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/xerial/sbt-pack.git</connection>
      <developerConnection>scm:git:git@github.com:xerial/sbt-pack.git</developerConnection>
      <url>https://github.com/xerial/sbt-pack</url>
    </scm>
    <developers>
      <developer>
        <id>leo</id>
        <name>Taro L. Saito</name>
        <url>http://xerial.org/leo</url>
      </developer>
    </developers>
}

// Add sonatype repository settings
publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

dynverSonatypeSnapshots in ThisBuild := true
