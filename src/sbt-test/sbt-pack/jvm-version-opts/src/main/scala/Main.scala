object Main {
  def main(args: Array[String]): Unit = {
    // Print Java version and JVM options for testing
    val javaVersion = System.getProperty("java.version")
    println(s"Java version: $javaVersion")
    
    // Extract major version number
    val majorVersion = if (javaVersion.startsWith("1.")) {
      javaVersion.split("\\.")(1).toInt
    } else {
      javaVersion.split("\\.")(0).split("-")(0).toInt
    }
    println(s"Major version: $majorVersion")
    
    val runtimeMxBean = java.lang.management.ManagementFactory.getRuntimeMXBean
    val jvmArgs = runtimeMxBean.getInputArguments
    
    println("JVM Arguments:")
    import scala.jdk.CollectionConverters._
    val argsList = jvmArgs.asScala.toList
    argsList.foreach { arg =>
      println(s"  $arg")
    }
    
    // Validate that we have the base JVM options
    val hasXms = argsList.exists(_.contains("-Xms256m"))
    val hasXmx = argsList.exists(_.contains("-Xmx512m"))
    
    println("\nValidation:")
    println(s"  Has -Xms256m: $hasXms")
    println(s"  Has -Xmx512m: $hasXmx")
    
    // Validate version-specific options based on current Java version
    // Using range-based logic: options are applied from the highest version <= current version
    val expectedVersionOptions = majorVersion match {
      case _ if majorVersion >= 8 && majorVersion < 11 =>
        // Java [8,11) should have MaxPermSize
        val hasMaxPermSize = argsList.exists(_.contains("MaxPermSize"))
        println(s"  Has MaxPermSize (Java [8,11)): $hasMaxPermSize")
        hasMaxPermSize
      case _ if majorVersion >= 11 && majorVersion < 17 =>
        // Java [11,17) should have UseJVMCICompiler
        val hasJVMCI = argsList.exists(_.contains("UseJVMCICompiler"))
        println(s"  Has UseJVMCICompiler (Java [11,17)): $hasJVMCI")
        hasJVMCI
      case _ if majorVersion >= 17 && majorVersion < 21 =>
        // Java [17,21) should have UseZGC
        val hasZGC = argsList.exists(_.contains("UseZGC"))
        println(s"  Has UseZGC (Java [17,21)): $hasZGC")
        hasZGC
      case _ if majorVersion >= 21 && majorVersion < 24 =>
        // Java [21,24) should have UseZGC and ZGenerational
        val hasZGC = argsList.exists(_.contains("UseZGC"))
        val hasGenZGC = argsList.exists(_.contains("ZGenerational"))
        println(s"  Has UseZGC (Java [21,24)): $hasZGC")
        println(s"  Has ZGenerational (Java [21,24)): $hasGenZGC")
        hasZGC && hasGenZGC
      case _ if majorVersion >= 24 =>
        // Java [24,∞) should have UseG1GC, G1HeapRegionSize, and sun-misc-unsafe-memory-access
        val hasG1GC = argsList.exists(_.contains("UseG1GC"))
        val hasG1HeapRegion = argsList.exists(_.contains("G1HeapRegionSize"))
        val hasUnsafeMemoryAccess = argsList.exists(_.contains("sun-misc-unsafe-memory-access=allow"))
        println(s"  Has UseG1GC (Java [24,∞)): $hasG1GC")
        println(s"  Has G1HeapRegionSize (Java [24,∞)): $hasG1HeapRegion")
        println(s"  Has --sun-misc-unsafe-memory-access=allow (Java [24,∞)): $hasUnsafeMemoryAccess")
        hasG1GC && hasG1HeapRegion && hasUnsafeMemoryAccess
      case _ =>
        // For versions before 8 or when no options are defined
        println(s"  No version-specific options for Java $majorVersion")
        true
    }
    
    // Exit with error if validation fails
    if (!hasXms || !hasXmx) {
      println("\nERROR: Base JVM options not found!")
      System.exit(1)
    }
    
    if (!expectedVersionOptions) {
      println(s"\nERROR: Expected version-specific JVM options for Java $majorVersion not found!")
      System.exit(1)
    }
    
    println("\nAll validations passed!")
  }
}