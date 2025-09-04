enablePlugins(PackPlugin)
name := "jvm-version-opts-test"

scalaVersion := "2.13.16"
crossPaths := false

packMain := Map("test" -> "Main")
packJvmOpts := Map("test" -> Seq("-Xms256m", "-Xmx512m"))

// Configure different JVM options for different Java versions
packJvmVersionSpecificOpts := Map(
  "test" -> Map(
    8 -> Seq("-XX:MaxPermSize=256m"),
    11 -> Seq("-XX:+UnlockExperimentalVMOptions", "-XX:+UseJVMCICompiler"),
    17 -> Seq("-XX:+UseZGC"),
    21 -> Seq("-XX:+UseZGC", "-XX:+GenerationalZGC"),
    24 -> Seq("-XX:+UseG1GC", "-XX:G1HeapRegionSize=32m")
  )
)