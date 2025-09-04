enablePlugins(PackPlugin)
name := "jvm-version-opts-test"

scalaVersion := "2.13.16"
crossPaths := false

packMain := Map("test" -> "Main")
packJvmOpts := Map("test" -> Seq("-Xms256m", "-Xmx512m"))

// Configure different JVM options for different Java versions
// These are applied as ranges: [8,11), [11,17), [17,21), [21,24), [24,∞)
packJvmVersionSpecificOpts := Map(
  "test" -> Map(
    8 -> Seq("-XX:MaxPermSize=256m"),
    11 -> Seq("-XX:+UnlockExperimentalVMOptions", "-XX:+UseJVMCICompiler"),
    17 -> Seq("-XX:+UseZGC"),
    21 -> Seq("-XX:+UseZGC", "-XX:+ZGenerational"),
    24 -> Seq("-XX:+UseG1GC", "-XX:G1HeapRegionSize=32m", "--sun-misc-unsafe-memory-access=allow")
  )
)