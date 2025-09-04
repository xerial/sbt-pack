package xerial.sbt.pack

object LaunchScript {

  def generateLaunchScript(opts: Opts, expandedClasspath: Option[String]): String = {
    val content = xerial.sbt.pack.txt.launch.render(opts, expandedClasspath)
    content.toString
  }
  def generateBatScript(opts: Opts, expandedClasspath: Option[String]): String = {
    val content = xerial.sbt.pack.txt.launch_bat.render(opts, expandedClasspath)
    content.toString
  }
  def generateMakefile(PROG_NAME: String, PROG_SYMLINK: String): String = {
    val content = xerial.sbt.pack.txt.Makefile.render(PROG_NAME, PROG_SYMLINK)
    content.toString
  }

  case class Opts(
      MAIN_CLASS: String,
      PROG_NAME: String,
      PROG_VERSION: String,
      PROG_REVISION: String,
      JVM_OPTS: String = "",
      JVM_VERSION_OPTS: Map[Int, String] = Map.empty,
      EXTRA_CLASSPATH: String,
      MAC_ICON_FILE: String = "icon-mac.png",
      ENV_VARS: String = ""
  )
}
