//--------------------------------------
//
// Pack.scala
// Since: 2012/11/19 4:12 PM
//
//--------------------------------------

package xerial.sbt

import sbt._
import classpath.ClasspathUtilities
import Keys._
import xerial.core.io.Resource
import java.io.ByteArrayOutputStream

/**
 * Plugin for packaging projects
 * @author Taro L. Saito
 */
object Pack extends sbt.Plugin {

  val pack: TaskKey[File] = TaskKey[File]("pack", "create a distributable package of the project")
  val packMain: SettingKey[Map[String, String]] = SettingKey[Map[String, String]]("packMain", "prog_name -> main class table")
  val packDir: SettingKey[String] = SettingKey[String]("pack-dir")
  val packExclude = SettingKey[Seq[String]]("pack-exclude")
  val packAllClasspaths = TaskKey[Seq[Classpath]]("pack-all-classpaths")
  val packLibJars = TaskKey[Seq[File]]("pack-lib-jars")
  val packUpdateReports = TaskKey[Seq[sbt.UpdateReport]]("pack-dependent-modules")
  val packMacIconFile = SettingKey[String]("pack-mac-icon-file", "icon file name for Mac")
  val packResourceDir = SettingKey[String]("pack-resource-dir", "pack resource directory. default = src/pack")
  val packAllUnmanagedJars = TaskKey[Seq[Classpath]]("pack-all-unmanaged")
  val packJvmOpts = SettingKey[Map[String, Seq[String]]]("pack-jvm-opts")

  val packSettings = Seq[sbt.Project.Setting[_]](
    packDir := "pack",
    packMain := Map.empty,
    packExclude := Seq.empty,
    packMacIconFile := "icon-mac.png",
    packResourceDir := "src/pack",
    packJvmOpts := Map.empty,
    packAllClasspaths <<= (thisProjectRef, buildStructure) flatMap getFromAllProjects(dependencyClasspath.task in Runtime),
    packAllUnmanagedJars <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(unmanagedJars.task in Compile),
    packLibJars <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(packageBin.task in Runtime),
    packUpdateReports <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(update.task)
  ) ++ Seq(packTask)

  private def getFromAllProjects[T](targetTask: SettingKey[Task[T]])(currentProject: ProjectRef, structure: Load.BuildStructure): Task[Seq[T]] =
    getFromSelectedProjects(targetTask)(currentProject, structure, Seq.empty)


  private def getFromSelectedProjects[T](targetTask: SettingKey[Task[T]])(currentProject: ProjectRef, structure: Load.BuildStructure, exclude: Seq[String]): Task[Seq[T]] = {
    def allProjectRefs(currentProject: ProjectRef): Seq[ProjectRef] = {
      def isExcluded(p: ProjectRef) = exclude.contains(p.project)
      val children = Project.getProject(currentProject, structure).toSeq.flatMap(_.aggregate)
      (currentProject +: (children flatMap (allProjectRefs(_)))) filterNot (isExcluded)
    }

    val projects: Seq[ProjectRef] = allProjectRefs(currentProject)
    projects.flatMap(p => targetTask in p get structure.data).join
  }

  private case class ModuleEntry(org: String, name: String, revision: String) {
    override def toString = "%s:%s:%s".format(org, name, revision)
  }

  private implicit def moduleEntryOrdering = Ordering.by[ModuleEntry, (String, String, String)](m => (m.org, m.name, m.revision))

  private def packTask = pack <<= (name, packMain, packDir, version, packLibJars, streams, target, baseDirectory, packUpdateReports, packMacIconFile, packResourceDir, packJvmOpts, packAllUnmanagedJars) map {
    (name, mainTable, packDir, ver, libs, out, target, base, reports, macIcon, resourceDir, jvmOpts, unmanaged) => {

      val dependentJars = collection.immutable.SortedMap.empty[ModuleEntry, File] ++    (for {
          r <- reports
          c <- r.configurations if c.configuration == "runtime"
          m <- c.modules
          (artifact, file) <- m.artifacts if DependencyFilter.allPass(c.configuration, m.module, artifact)} yield {
          val mid = m.module
          ModuleEntry(mid.organization, mid.name, mid.revision) -> file
        })

      def rpath(f: RichFile) = f.relativeTo(base) map {
        _.toString
      } getOrElse (f.toString)

      val distDir = target / packDir
      out.log.info("Creating a distributable package in " + rpath(distDir))
      IO.delete(distDir)
      distDir.mkdirs()

      val libDir = distDir / "lib"
      out.log.info("Copying libraries to " + rpath(libDir))
      libDir.mkdirs()
      out.log.info("project jars:\n" + libs.mkString("\n"))
      libs.foreach(l => IO.copyFile(l, libDir / l.getName))
      out.log.info("project dependencies:\n" + dependentJars.keys.mkString("\n"))
      for ((m, f) <- dependentJars) {
        IO.copyFile(f, libDir / "%s-%s.jar".format(m.name, m.revision))
      }
      out.log.info("unmanaged dependencies:")
      for(m <- unmanaged; um <- m; f = um.data) {
        out.log.info(f.getPath)
        IO.copyFile(f, libDir / f.getName)
      }

      val binDir = distDir / "bin"
      out.log.info("Create a bin folder: " + rpath(binDir))
      binDir.mkdirs()

      def read(path: String): String = Resource.open(this.getClass, path) {
        f =>
          val b = new ByteArrayOutputStream
          val buf = new Array[Byte](8192)
          var ret = 0
          while ({ret = f.read(buf, 0, buf.length); ret != -1}) {
            b.write(buf, 0, ret)
          }
          new String(b.toByteArray)
      }


      def write(path: String, content: String) {
        val p = distDir / path
        out.log.info("Generating %s".format(rpath(p)))
        IO.write(p, content)
      }

      // Create launch scripts
      out.log.info("Generating launch scripts")
      if (mainTable.isEmpty) {
        out.log.warn("No mapping (progran name) -> MainClass is defined. Please set packMain variable (Map[String, String]) in your sbt project settings.")
      }

      for ((name, mainClass) <- mainTable) {
        out.log.info("main class for %s: %s".format(name, mainClass))
        val m = Map(
          "PROG_NAME" -> name,
          "MAIN_CLASS" -> mainClass,
          "MAC_ICON_FILE" -> macIcon,
          "JVM_OPTS" -> jvmOpts.getOrElse(name, Nil).map("\"%s\"".format(_)).mkString(" "))
        val launchScript = StringTemplate.eval(read("pack/script/launch.template"))(m)
        val progName = m("PROG_NAME").replaceAll(" ", "") // remove white spaces
        write("bin/%s".format(progName), launchScript)
      }

      // Create Makefile
      val makefile = {
        val globalVar = Map("PROG_NAME" -> name)
        val b = new StringBuilder
        b.append(StringTemplate.eval(read("pack/script/Makefile.template"))(globalVar))
        for(p <- mainTable.keys) {
          b.append("\t")
          b.append("""ln -sf "../$(PROG)/current/bin/%s" "$(PREFIX)/bin/%s"""".format(p, p))
          b.append("+n")
        }
        b.result
      }


      val otherResourceDir = base / resourceDir
      val binScriptsDir = otherResourceDir / "bin"
      val additionalLines: Array[String] = for (script <- Option(binScriptsDir.listFiles) getOrElse Array.empty) yield {
        "\t" + """ln -sf "../$(PROG)/current/bin/%s" "$(PREFIX)/bin/%s"""".format(script.getName, script.getName)
      }

      write("Makefile", makefile + additionalLines.mkString("\n") + "\n")

      // Copy other scripts
      IO.copyDirectory(otherResourceDir, distDir)

      // chmod +x the bin directory
      if (!System.getProperty("os.name", "").contains("Windows")) {
        scala.sys.process.Process("chmod -R +x %s".format(binDir)).run
      }

      // Output the version number
      write("VERSION", "version:=" + ver + "\n")
      out.log.info("done.")
      distDir
   }
  }


}

