package xerial.sbt.pack

import wvlet.airspec.AirSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

/** Compares generated scripts with golden files in src/test/resources/xerial/sbt/pack/golden. Set UPDATE_GOLDEN=1 to
  * regenerate them.
  */
class LaunchScriptSpec extends AirSpec {
  private val goldenDir = Paths.get("src/test/resources/xerial/sbt/pack/golden")

  private def checkGolden(name: String, actual: String): Unit = {
    val file = goldenDir.resolve(name)
    if (sys.env.get("UPDATE_GOLDEN").contains("1") || !Files.exists(file)) {
      Files.createDirectories(goldenDir)
      Files.write(file, actual.getBytes(StandardCharsets.UTF_8))
    }
    val expected = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
    if (actual != expected) {
      // Save the actual output to make it easy to diff with the golden file
      val actualDir = Paths.get("target/golden-actual")
      Files.createDirectories(actualDir)
      Files.write(actualDir.resolve(name), actual.getBytes(StandardCharsets.UTF_8))
    }
    actual shouldBe expected
  }

  private val minimalOpts = LaunchScript.Opts(
    MAIN_CLASS = "org.example.Main",
    PROG_NAME = "hello",
    PROG_VERSION = "1.0.0",
    PROG_REVISION = "abc1234",
    EXTRA_CLASSPATH = ""
  )

  private val fullOpts = minimalOpts.copy(
    JVM_OPTS = "\"-Xmx512m\" \"-Dfoo=bar\"",
    JVM_VERSION_OPTS =
      Map(24 -> "\"--enable-native-access=ALL-UNNAMED\"", 17 -> "\"--add-opens=java.base/java.lang=ALL-UNNAMED\""),
    EXTRA_CLASSPATH = "${PROG_HOME}/etc:",
    MAC_ICON_FILE = "custom-icon.png",
    ENV_VARS = "FOO=1 BAR=2"
  )

  private val expandedCp = Some("${PROG_HOME}/lib/a.jar:${PROG_HOME}/lib/b.jar")

  test("launch script") {
    checkGolden("launch-minimal.sh", LaunchScript.generateLaunchScript(minimalOpts, None))
    checkGolden("launch-full.sh", LaunchScript.generateLaunchScript(fullOpts, None))
    checkGolden("launch-expanded-cp.sh", LaunchScript.generateLaunchScript(fullOpts, expandedCp))
  }

  test("bat script") {
    checkGolden("launch-minimal.bat", LaunchScript.generateBatScript(minimalOpts, None))
    checkGolden("launch-full.bat", LaunchScript.generateBatScript(fullOpts, None))
    checkGolden("launch-expanded-cp.bat", LaunchScript.generateBatScript(fullOpts, expandedCp))
  }

  test("Makefile") {
    val symlinks = Seq("hello", "world")
      .map(n => "\t" + s"""ln -sf "../$$(PROG)/current/bin/$n" "$$(PREFIX)/bin/$n"""")
      .mkString("\n")
    checkGolden("Makefile", LaunchScript.generateMakefile("hello", symlinks))
  }
}
