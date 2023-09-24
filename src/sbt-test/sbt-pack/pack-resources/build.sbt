lazy val root = Project(
  id = "pack-resources",
  base = file(".")
).enablePlugins(PackPlugin)
  .settings(
    scalaVersion := "2.12.18",
    // Copy files from ${root}/web/... to ${root}/target/pack/web-content...
    packResourceDir += (baseDirectory.value / "web" -> "web-content"),
    // custom settings here
    crossPaths := false
  )
