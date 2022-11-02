import xerial.sbt.Sonatype._

publishMavenStyle      := true
sonatypeProfileName    := "org.xerial"
sonatypeProjectHosting := Some(GitHubHosting(user = "xerial", repository = "sbt-pack", email = "leo@xerial.org"))
developers := List(
  Developer(id = "leo", name = "Taro L. Saito", email = "leo@xerial.org", url = url("http://xerial.org/leo"))
)
licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

publishTo := sonatypePublishToBundle.value

ThisBuild / dynverSonatypeSnapshots := true
