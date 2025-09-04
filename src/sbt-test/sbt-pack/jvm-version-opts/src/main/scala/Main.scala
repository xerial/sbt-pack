object Main {
  def main(args: Array[String]): Unit = {
    // Print Java version and JVM options for testing
    val javaVersion = System.getProperty("java.version")
    println(s"Java version: $javaVersion")
    
    val runtimeMxBean = java.lang.management.ManagementFactory.getRuntimeMXBean
    val jvmArgs = runtimeMxBean.getInputArguments
    
    println("JVM Arguments:")
    import scala.collection.JavaConverters._
    jvmArgs.asScala.foreach { arg =>
      println(s"  $arg")
    }
  }
}