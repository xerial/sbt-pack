//--------------------------------------
//
// Pack.scala
// Since: 2012/11/19 4:12 PM
//
//--------------------------------------

package xerial.sbt

import sbt._
import org.fusesource.scalate.TemplateEngine
import classpath.ClasspathUtilities
import Keys._
import java.io.ByteArrayOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.File.pathSeparator
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream
import org.kamranzafar.jtar.TarOutputStream
import org.kamranzafar.jtar.TarEntry

/**
 * Plugin for packaging projects
 * @author Taro L. Saito
 */
object Pack extends sbt.Plugin {

  val pack: TaskKey[File] = TaskKey[File]("pack", "create a distributable package of the project")
  val packArchive: TaskKey[File] = TaskKey[File]("pack-archive", "create an archive of the distributable package")
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
  val packExtraClasspath = SettingKey[Map[String, Seq[String]]]("pack-extra-classpath")

  val packSettings = Seq[sbt.Project.Setting[_]](
    packDir := "pack",
    packMain := Map.empty,
    packExclude := Seq.empty,
    packMacIconFile := "icon-mac.png",
    packResourceDir := "src/pack",
    packJvmOpts := Map.empty,
    packExtraClasspath := Map.empty,
    packAllClasspaths <<= (thisProjectRef, buildStructure) flatMap getFromAllProjects(dependencyClasspath.task in Runtime),
    packAllUnmanagedJars <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(unmanagedJars.task in Compile),
    packLibJars <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(packageBin.task in Runtime),
    packUpdateReports <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(update.task)
  ) ++ Seq(packTask, packArchiveTask)

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

  private case class ModuleEntry(org: String, name: String, revision: String, classifier:Option[String]) {
    private def classifierSuffix = classifier.map("-" + _).getOrElse("")
    override def toString = "%s:%s:%s%s".format(org, name, revision, classifierSuffix)
    def jarName = "%s-%s%s.jar".format(name, revision, classifierSuffix)
  }

  private implicit def moduleEntryOrdering = Ordering.by[ModuleEntry, (String, String, String)](m => (m.org, m.name, m.revision))

  private def rpath(base: File, f: RichFile) = f.relativeTo(base).getOrElse(f).toString

  private def packArchiveTask = packArchive <<= (pack in Compile, name, version, streams, target, baseDirectory) map { (distDir, name, ver, out, target, base) =>
    val binDir = distDir / "bin"
    val archiveRoot = name + "-" + ver
    val archiveName = archiveRoot + ".tar.gz"
    out.log.info("Generating " + rpath(base, target / archiveName))
    val tarfile = new TarOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(target / (archiveRoot + ".tar.gz"))) {
      `def`.setLevel(Deflater.BEST_COMPRESSION)
    }))
    def tarEntry(src: File, dst: String) {
      val tarEntry = new TarEntry(src, dst)
      tarEntry.setIds(0, 0)
      tarEntry.setUserName("")
      tarEntry.setGroupName("")
      if(src.getAbsolutePath startsWith binDir.getAbsolutePath)
        tarEntry.getHeader.mode = 0755
      tarfile.putNextEntry(tarEntry)
    }
    tarEntry(new File("."), archiveRoot)
    val excludeFiles = Set("Makefile", "VERSION")
    val buffer = Array.fill(1024 * 1024)(0: Byte)
    def addFilesToTar(dir: File): Unit = dir.listFiles.
      filterNot(f => excludeFiles.contains(rpath(distDir, f))).foreach { file =>
        tarEntry(file, archiveRoot ++ "/" ++ rpath(distDir, file))
        if(file.isDirectory) addFilesToTar(file)
        else {
          def copy(input: InputStream): Unit = input.read(buffer) match {
            case length if length < 0 => input.close()
            case length =>
              tarfile.write(buffer, 0, length)
              copy(input)
          }
          copy(new BufferedInputStream(new FileInputStream(file)))
        }
      }
    addFilesToTar(distDir)
    tarfile.close()

    target / archiveName
  }

  private def packTask = pack <<= (name, packMain, packDir, version, packLibJars, streams, target, baseDirectory, packUpdateReports, packMacIconFile, packResourceDir, packJvmOpts, packExtraClasspath, packAllUnmanagedJars) map {
    (name, mainTable, packDir, ver, libs, out, target, base, reports, macIcon, resourceDir, jvmOpts, extraClasspath, unmanaged) => {

      val dependentJars = collection.immutable.SortedMap.empty[ModuleEntry, File] ++    (for {
          r <- reports
          c <- r.configurations if c.configuration == "runtime"
          m <- c.modules
          (artifact, file) <- m.artifacts if DependencyFilter.allPass(c.configuration, m.module, artifact)} yield {
          val mid = m.module
          ModuleEntry(mid.organization, mid.name, mid.revision, artifact.classifier) -> file
        })

      val distDir = target / packDir
      out.log.info("Creating a distributable package in " + rpath(base, distDir))
      IO.delete(distDir)
      distDir.mkdirs()

      val libDir = distDir / "lib"
      out.log.info("Copying libraries to " + rpath(base, libDir))
      libDir.mkdirs()
      out.log.info("project jars:\n" + libs.mkString("\n"))
      libs.foreach(l => IO.copyFile(l, libDir / l.getName))
      out.log.info("project dependencies:\n" + dependentJars.keys.mkString("\n"))
      for ((m, f) <- dependentJars) {
        IO.copyFile(f, libDir / m.jarName)
      }
      out.log.info("unmanaged dependencies:")
      for(m <- unmanaged; um <- m; f = um.data) {
        out.log.info(f.getPath)
        IO.copyFile(f, libDir / f.getName)
      }

      val binDir = distDir / "bin"
      out.log.info("Create a bin folder: " + rpath(base, binDir))
      binDir.mkdirs()

      def write(path: String, content: String) {
        val p = distDir / path
        out.log.info("Generating %s".format(rpath(base, p)))
        IO.write(p, content)
      }

      // Create launch scripts
      out.log.info("Generating launch scripts")
      if (mainTable.isEmpty) {
        out.log.warn("No mapping (progran name) -> MainClass is defined. Please set packMain variable (Map[String, String]) in your sbt project settings.")
      }

      val engine = new TemplateEngine

      for ((name, mainClass) <- mainTable) {
        out.log.info("main class for %s: %s".format(name, mainClass))
        val m = Map(
          "PROG_NAME" -> name,
          "MAIN_CLASS" -> mainClass,
          "MAC_ICON_FILE" -> macIcon,
          "JVM_OPTS" -> jvmOpts.getOrElse(name, Nil).map("\"%s\"".format(_)).mkString(" "),
          "EXTRA_CLASSPATH" -> extraClasspath.get(name).map(_.mkString("", pathSeparator, pathSeparator)).orElse(Some("")).get)
        val launchScript = engine.layout("/xerial/sbt/template/launch.mustache", m)
        val progName = m("PROG_NAME").replaceAll(" ", "") // remove white spaces
        write("bin/%s".format(progName), launchScript)
      }

      val otherResourceDir = base / resourceDir
      val binScriptsDir = otherResourceDir / "bin"

      def linkToScript(name:String) = 
         "\t" + """ln -sf "../$(PROG)/current/bin/%s" "$(PREFIX)/bin/%s"""".format(name, name)

      // Create Makefile
      val makefile = {
        val additinalScripts = (Option(binScriptsDir.listFiles) getOrElse Array.empty).map(_.getName)
        val symlink = (mainTable.keys ++ additinalScripts).map(linkToScript).mkString("\n")
        val globalVar = Map("PROG_NAME" -> name, "PROG_SYMLINK" -> symlink)
        engine.layout("/xerial/sbt/template/Makefile.mustache", globalVar)
      }
      write("Makefile", makefile)

      // Output the version number
      write("VERSION", "version:=" + ver + "\n")

      // Copy other scripts
      IO.copyDirectory(otherResourceDir, distDir)

      // chmod +x the scripts in bin directory
      binDir.listFiles.foreach(_.setExecutable(true, false))

      out.log.info("done.")
      distDir
   }
  }


}

