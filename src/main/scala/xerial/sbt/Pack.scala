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

  private case class ModuleEntry(org: String, name: String, revision: String, classifier: Option[String], originalFileName: String) {
    private def classifierSuffix = classifier.map("-" + _).getOrElse("")

    override def toString = "%s:%s:%s%s".format(org, name, revision, classifierSuffix)

    def jarName = "%s-%s%s.jar".format(name, revision, classifierSuffix)
  }

  private implicit def moduleEntryOrdering = Ordering.by[ModuleEntry, (String, String, String, Option[String])](m => (m.org, m.name, m.revision, m.classifier))

  val runtimeFilter = ScopeFilter(inAnyProject, inConfigurations(Runtime))

  val pack = taskKey[File]("create a distributable package of the project")
  val packDir = settingKey[String]("pack-dir")
  val packUpdateReports = taskKey[Seq[sbt.UpdateReport]]("only for retrieving dependent module names")
  val packArchive = TaskKey[File]("pack-archive", "create a tar.gz archive of the distributable package")
  val packMain = settingKey[Map[String, String]]("prog_name -> main class table")
  val packExclude = SettingKey[Seq[String]]("pack-exclude", "specify projects to exclude when packaging")
  val packAllClasspaths = TaskKey[Seq[Classpath]]("pack-all-classpaths")
  val packLibJars = TaskKey[Seq[File]]("pack-lib-jars")

  val packMacIconFile = SettingKey[String]("pack-mac-icon-file", "icon file name for Mac")
  val packResourceDir = SettingKey[String]("pack-resource-dir", "pack resource directory. default = src/pack")
  val packAllUnmanagedJars = taskKey[Seq[Classpath]]("all unmanaged jar files")
  val packJvmOpts = SettingKey[Map[String, Seq[String]]]("pack-jvm-opts")
  val packExtraClasspath = SettingKey[Map[String, Seq[String]]]("pack-extra-classpath")
  val packPreserveOriginalJarName = SettingKey[Boolean]("pack-preserve-jarname", "preserve the original jar file names. default = false")

  lazy val packSettings = Seq[Def.Setting[_]](
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
    packUpdateReports <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(update.task),
    packPreserveOriginalJarName := false,
    pack := {
      val dependentJars = collection.immutable.SortedMap.empty[ModuleEntry, File] ++ (
        for {
          r: sbt.UpdateReport <- packUpdateReports.value
          c <- r.configurations if c.configuration == "runtime"
          m <- c.modules
          (artifact, file) <- m.artifacts if DependencyFilter.allPass(c.configuration, m.module, artifact)}
        yield {
          val mid = m.module
          val me = ModuleEntry(mid.organization, mid.name, mid.revision, artifact.classifier, file.getName)
          me -> file
        })

      val out = streams.value
      val distDir: File = target.value / packDir.value
      out.log.info("Creating a distributable package in " + rpath(baseDirectory.value, distDir))
      IO.delete(distDir)
      distDir.mkdirs()

      // Create target/pack/lib folder
      val libDir = distDir / "lib"
      libDir.mkdirs()

      // Copy project jars
      val base: File = baseDirectory.value
      out.log.info("Copying libraries to " + rpath(base, libDir))
      val libs: Seq[File] = packLibJars.value
      out.log.info("project jars:\n" + libs.map(path => rpath(base, path)).mkString("\n"))
      libs.foreach(l => IO.copyFile(l, libDir / l.getName))

      // Copy dependent jars
      out.log.info("project dependencies:\n" + dependentJars.keys.mkString("\n"))
      for ((m, f) <- dependentJars) {
        val targetFileName = if (packPreserveOriginalJarName.value) m.originalFileName else m.jarName
        IO.copyFile(f, libDir / targetFileName, true)
      }

      // Copy unmanaged jars in ${baseDir}/lib folder
      out.log.info("unmanaged dependencies:")
      for (m <- packAllUnmanagedJars.value; um <- m; f = um.data) {
        out.log.info(f.getPath)
        IO.copyFile(f, libDir / f.getName, true)
      }

      // Create target/pack/bin folder
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
      val mainTable: Map[String, String] = packMain.value
      if (mainTable.isEmpty) {
        out.log.warn("No mapping (program name) -> MainClass is defined. Please set packMain variable (Map[String, String]) in your sbt project settings.")
      }
      // Render script via Scalate template
      val engine = new TemplateEngine
      for ((name, mainClass) <- mainTable) {
        out.log.info("main class for %s: %s".format(name, mainClass))
        val m = Map(
          "PROG_NAME" -> name,
          "MAIN_CLASS" -> mainClass,
          "MAC_ICON_FILE" -> packMacIconFile.value,
          "JVM_OPTS" -> packJvmOpts.value.getOrElse(name, Nil).map("\"%s\"".format(_)).mkString(" "),
          "EXTRA_CLASSPATH" -> packExtraClasspath.value.get(name).map(_.mkString("", pathSeparator, pathSeparator)).orElse(Some("")).get)
        val launchScript = engine.layout("/xerial/sbt/template/launch.mustache", m)
        val progName = m("PROG_NAME").replaceAll(" ", "") // remove white spaces
        write("bin/%s".format(progName), launchScript)
      }

      // Copy resources in src/pack folder
      val otherResourceDir = base / packResourceDir.value
      val binScriptsDir = otherResourceDir / "bin"

      def linkToScript(name: String) =
        "\t" + """ln -sf "../$(PROG)/current/bin/%s" "$(PREFIX)/bin/%s"""".format(name, name)

      // Create Makefile
      val makefile = {
        val additinalScripts = (Option(binScriptsDir.listFiles) getOrElse Array.empty).map(_.getName)
        val symlink = (mainTable.keys ++ additinalScripts).map(linkToScript).mkString("\n")
        val globalVar = Map("PROG_NAME" -> name.value, "PROG_SYMLINK" -> symlink)
        engine.layout("/xerial/sbt/template/Makefile.mustache", globalVar)
      }
      write("Makefile", makefile)

      // Output the version number
      write("VERSION", "version:=" + version.value + "\n")

      // Copy other scripts
      IO.copyDirectory(otherResourceDir, distDir, overwrite=true, preserveLastModified=true)

      // chmod +x the scripts in bin directory
      binDir.listFiles.foreach(_.setExecutable(true, false))

      out.log.info("done.")
      distDir
    },
    packArchive := {
      val out = streams.value
      val targetDir: File = target.value
      val distDir: File = pack.value
      val binDir = distDir / "bin"
      val archiveRoot = name.value + "-" + version.value
      val archiveName = archiveRoot + ".tar.gz"
      out.log.info("Generating " + rpath(baseDirectory.value, targetDir / archiveName))
      val tarfile = new TarOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(targetDir / (archiveRoot + ".tar.gz"))) {
        `def`.setLevel(Deflater.BEST_COMPRESSION)
      }))
      def tarEntry(src: File, dst: String) {
        val tarEntry = new TarEntry(src, dst)
        tarEntry.setIds(0, 0)
        tarEntry.setUserName("")
        tarEntry.setGroupName("")
        if (src.getAbsolutePath startsWith binDir.getAbsolutePath)
          tarEntry.getHeader.mode = Integer.parseInt("0755", 8)
        tarfile.putNextEntry(tarEntry)
      }
      tarEntry(new File("."), archiveRoot)
      val excludeFiles = Set("Makefile", "VERSION")
      val buffer = Array.fill(1024 * 1024)(0: Byte)
      def addFilesToTar(dir: File): Unit = dir.listFiles.
        filterNot(f => excludeFiles.contains(rpath(distDir, f))).foreach {
        file =>
          tarEntry(file, archiveRoot ++ "/" ++ rpath(distDir, file))
          if (file.isDirectory) addFilesToTar(file)
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

      val archiveFile: File = target.value / archiveName
      archiveFile
    }
  )

  private def getFromAllProjects[T](targetTask: SettingKey[Task[T]])(currentProject: ProjectRef, structure: BuildStructure): Task[Seq[T]] =
    getFromSelectedProjects(targetTask)(currentProject, structure, Seq.empty)


  private def getFromSelectedProjects[T](targetTask: SettingKey[Task[T]])(currentProject: ProjectRef, structure: BuildStructure, exclude: Seq[String]): Task[Seq[T]] = {
    def allProjectRefs(currentProject: ProjectRef): Seq[ProjectRef] = {
      def isExcluded(p: ProjectRef) = exclude.contains(p.project)
      val children = Project.getProject(currentProject, structure).toSeq.flatMap {
        p =>
          p.uses
      }

      (currentProject +: (children flatMap (allProjectRefs(_)))) filterNot (isExcluded)
    }

    val projects: Seq[ProjectRef] = allProjectRefs(currentProject).distinct
    projects.flatMap(p => targetTask in p get structure.data).join
  }


  private def rpath(base: File, f: RichFile) = f.relativeTo(base).getOrElse(f).toString


}

