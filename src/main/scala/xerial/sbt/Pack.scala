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
  val packArchiveArtifact = SettingKey[Artifact]("tar.gz archive artifact")
  val packArchivePrefix = SettingKey[String]("prefix of (prefix)-(version).tar.gz archive file name")
  val packMain = settingKey[Map[String, String]]("prog_name -> main class table")
  val packExclude = SettingKey[Seq[String]]("pack-exclude", "specify projects to exclude when packaging")
  val packAllClasspaths = TaskKey[Seq[Classpath]]("pack-all-classpaths")
  val packLibJars = TaskKey[Seq[File]]("pack-lib-jars")
  val packGenerateWindowsBatFile = settingKey[Boolean]("Generate BAT file launch scripts for Windows")

  val packMacIconFile = SettingKey[String]("pack-mac-icon-file", "icon file name for Mac")
  val packResourceDir = SettingKey[Seq[String]](s"pack-resource-dir", "pack resource directory. default = Seq($DEFAULT_RESOURCE_DIRECTORY)")
  val packAllUnmanagedJars = taskKey[Seq[Classpath]]("all unmanaged jar files")
  val packJvmOpts = SettingKey[Map[String, Seq[String]]]("pack-jvm-opts")
  val packExtraClasspath = SettingKey[Map[String, Seq[String]]]("pack-extra-classpath")
  val packPreserveOriginalJarName = SettingKey[Boolean]("pack-preserve-jarname", "preserve the original jar file names. default = false")
  
  val DEFAULT_RESOURCE_DIRECTORY = "src/pack"
  
  lazy val packSettings = Seq[Def.Setting[_]](
    packDir := "pack",
    packMain := Map.empty,
    packExclude := Seq.empty,
    packMacIconFile := "icon-mac.png",
    packResourceDir := Seq.empty,
    packJvmOpts := Map.empty,
    packExtraClasspath := Map.empty,
    packAllClasspaths <<= (thisProjectRef, buildStructure) flatMap getFromAllProjects(dependencyClasspath.task in Runtime),
    packAllUnmanagedJars <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(unmanagedJars.task in Compile),
    packLibJars <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(packageBin.task in Runtime),
    packUpdateReports <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(update.task),
    packPreserveOriginalJarName := false,
    packGenerateWindowsBatFile := true,
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
        }).filter(tuple => tuple._1.classifier == None)

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

      val progVersion = version.value
      val pathSeparator = "${PSEP}"
      // Render script via Scalate template
      val engine = new TemplateEngine


      for ((name, mainClass) <- mainTable) {
        out.log.info("main class for %s: %s".format(name, mainClass))
        def extraClasspath(sep:String) : String = packExtraClasspath.value.get(name).map(_.mkString("", sep, sep)).getOrElse("")
        val m = Map(
          "PROG_NAME" -> name,
          "PROG_VERSION" -> progVersion,
          "MAIN_CLASS" -> mainClass,
          "MAC_ICON_FILE" -> packMacIconFile.value,
          "JVM_OPTS" -> packJvmOpts.value.getOrElse(name, Nil).map("\"%s\"".format(_)).mkString(" "),
          "EXTRA_CLASSPATH" -> extraClasspath(pathSeparator))
        val launchScript = engine.layout("/xerial/sbt/template/launch.mustache", m)
        val progName = m("PROG_NAME").replaceAll(" ", "") // remove white spaces
        write(s"bin/$progName", launchScript)

        // Create BAT file
        if(packGenerateWindowsBatFile.value) {
          val extraPath = extraClasspath("%PSEP%").replaceAll("""\$\{PROG_HOME\}""", "%PROG_HOME%").replaceAll("/", """\\""")
          val propForWin : Map[String, Any] = m + ("EXTRA_CLASSPATH" -> extraPath)
          val batScript = engine.layout("/xerial/sbt/template/launch-bat.mustache", propForWin)
          write(s"bin/${progName}.bat", batScript)
        }
      }

      // Copy resources in src/pack folder
      val binScriptsDir = base / DEFAULT_RESOURCE_DIRECTORY / "bin"
      val otherResourceDirs = Seq(binScriptsDir) ++ packResourceDir.value.map( dir => base / dir )
      out.log.info(s"packed resource directories = ${otherResourceDirs.mkString(",")}")

      def linkToScript(name: String) =
        "\t" + """ln -sf "../$(PROG)/current/bin/%s" "$(PREFIX)/bin/%s"""".format(name, name)

      // Create Makefile
      val makefile = {
        val additionalScripts = (Option(binScriptsDir.listFiles) getOrElse Array.empty).map(_.getName)
        val symlink = (mainTable.keys ++ additionalScripts).map(linkToScript).mkString("\n")
        val globalVar = Map("PROG_NAME" -> name.value, "PROG_SYMLINK" -> symlink)
        engine.layout("/xerial/sbt/template/Makefile.mustache", globalVar)
      }
      write("Makefile", makefile)

      // Output the version number
      write("VERSION", "version:=" + progVersion + "\n")

      // Copy other scripts
      otherResourceDirs.foreach { otherResourceDir =>
        IO.copyDirectory(otherResourceDir, distDir, overwrite=true, preserveLastModified=true)
      }

      // chmod +x the scripts in bin directory
      binDir.listFiles.foreach(_.setExecutable(true, false))

      out.log.info("done.")
      distDir
    },
    packArchiveArtifact := Artifact(packArchivePrefix.value, "arch", "tar.gz"),
    packArchivePrefix := name.value,
    packArchive := {
      val out = streams.value
      val targetDir: File = target.value
      val distDir: File = pack.value
      val binDir = distDir / "bin"
      val archiveStem = s"${packArchivePrefix.value}-${version.value}"
      val archiveName = s"${archiveStem}.tar.gz"
      out.log.info("Generating " + rpath(baseDirectory.value, targetDir / archiveName))
      val tarfile = new TarOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(targetDir / archiveName)) {
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
      tarEntry(new File("."), archiveStem)
      val excludeFiles = Set("Makefile", "VERSION")
      val buffer = Array.fill(1024 * 1024)(0: Byte)
      def addFilesToTar(dir: File): Unit = dir.listFiles.
        filterNot(f => excludeFiles.contains(rpath(distDir, f))).foreach {
        file =>
          tarEntry(file, archiveStem ++ "/" ++ rpath(distDir, file))
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


  def publishPackArchive : SettingsDefinition = {
    val pkgd = packagedArtifacts := packagedArtifacts.value updated (packArchiveArtifact.value, packArchive.value)
    Seq( artifacts += packArchiveArtifact.value, pkgd )
  }


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

