package xerial.sbt.pack

import java.nio.charset.StandardCharsets

object LaunchScript {

  def generateLaunchScript(opts: Opts, expandedClasspath: Option[String]): String = {
    val classpath = expandedClasspath.getOrElse("${PROG_HOME}/lib/*")
    render(
      "launch.sh.template",
      opts.variables ++ Map(
        "JVM_VERSION_OPTS" -> versionOptsBlock(opts, "\n") { (version, versionOpts) =>
          s"""if [ "$$JAVA_VERSION" -ge ${version} ]; then
             |  VERSION_OPTS="${versionOpts}"
             |fi
             |""".stripMargin
        } {
          """if [ -n "$VERSION_OPTS" ]; then
              |  JAVA_OPTS="$JAVA_OPTS $VERSION_OPTS"
              |fi
              |""".stripMargin
        },
        "CLASSPATH" -> s""" -cp "'${opts.EXTRA_CLASSPATH}${classpath}$${CLASSPATH_SUFFIX}'" """
      )
    )
  }

  def generateBatScript(opts: Opts, expandedClasspath: Option[String]): String = {
    val classpath = expandedClasspath.getOrElse("%PROG_HOME%\\lib\\*")
    render(
      "launch.bat.template",
      opts.variables ++ Map(
        "JVM_VERSION_OPTS" -> versionOptsBlock(opts, "\r\n", header = "set \"VERSION_OPTS=\"\r\n") {
          (version, versionOpts) =>
            s"if %JAVA_VERSION% GEQ ${version} (\r\n  set \"VERSION_OPTS=${versionOpts}\"\r\n)\r\n"
        } { "if defined VERSION_OPTS (\r\n  set \"JAVA_OPTS=%JAVA_OPTS% %VERSION_OPTS%\"\r\n)\r\n" },
        "CLASSPATH" -> s"\"${opts.EXTRA_CLASSPATH}${classpath};\" "
      )
    )
  }

  def generateMakefile(PROG_NAME: String, PROG_SYMLINK: String): String = {
    render("Makefile.template", Map("PROG_NAME" -> PROG_NAME, "PROG_SYMLINK" -> PROG_SYMLINK))
  }

  /** Render the script block that adds JVM options for each Java version range. Returns an empty string if no
    * version-specific options are given.
    */
  private def versionOptsBlock(opts: Opts, nl: String, header: String = "")(
      versionCheck: (Int, String) => String
  )(footer: String): String = {
    if (opts.JVM_VERSION_OPTS.isEmpty) {
      ""
    } else {
      val checks = opts.JVM_VERSION_OPTS.keys.toList.sorted.map { version =>
        nl + versionCheck(version, opts.JVM_VERSION_OPTS(version))
      }
      nl + header + checks.mkString + nl + footer
    }
  }

  private val placeholder = """\{\{(\w+)\}\}""".r

  private def render(templateName: String, variables: Map[String, String]): String = {
    val template = loadTemplate(templateName)
    placeholder.replaceAllIn(
      template,
      m =>
        scala.util.matching.Regex.quoteReplacement(
          variables.getOrElse(m.group(1), sys.error(s"Unknown variable {{${m.group(1)}}} in ${templateName}"))
        )
    )
  }

  private def loadTemplate(templateName: String): String = {
    val in = getClass.getResourceAsStream(s"/xerial/sbt/pack/${templateName}")
    if (in == null) {
      sys.error(s"Template ${templateName} is not found")
    }
    try new String(in.readAllBytes(), StandardCharsets.UTF_8)
    finally in.close()
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
  ) {
    private[LaunchScript] def variables: Map[String, String] = Map(
      "MAIN_CLASS"    -> MAIN_CLASS,
      "PROG_NAME"     -> PROG_NAME,
      "PROG_VERSION"  -> PROG_VERSION,
      "PROG_REVISION" -> PROG_REVISION,
      "JVM_OPTS"      -> JVM_OPTS,
      "MAC_ICON_FILE" -> MAC_ICON_FILE,
      "ENV_VARS"      -> ENV_VARS
    )
  }
}
