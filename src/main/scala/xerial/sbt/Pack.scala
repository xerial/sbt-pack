//--------------------------------------
//
// Pack.scala
// Since: 2012/11/19 4:12 PM
//
//--------------------------------------

package xerial.sbt

import sbt._
import org.fusesource.scalate.TemplateEngine
import Keys._
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream
import org.kamranzafar.jtar.TarOutputStream
import org.kamranzafar.jtar.TarEntry
import scala.collection.mutable

/**
 * Plugin for packaging projects
 * @author Taro L. Saito
 */
object Pack extends sbt.Plugin {

  private case class ModuleEntry(org: String,
                                 name: String,
                                 revision: VersionString,
                                 classifier: Option[String],
                                 originalFileName: String,
                                 projectRef: ProjectRef) {
    private def classifierSuffix = classifier.map("-" + _).getOrElse("")

    override def toString = "%s:%s:%s%s".format(org, name, revision, classifierSuffix)

    def jarName = "%s-%s%s.jar".format(name, revision, classifierSuffix)

    def fullJarName = "%s.%s-%s%s.jar".format(org, name, revision, classifierSuffix)

    def noVersionJarName = "%s.%s%s.jar".format(org, name, classifierSuffix)
  }

  private implicit def versionStringOrdering = DefaultVersionStringOrdering
  private implicit def moduleEntryOrdering = Ordering.by[ModuleEntry, (String, String, VersionString, Option[String])](m => (m.org, m.name, m.revision, m.classifier))

  val runtimeFilter = ScopeFilter(inAnyProject, inConfigurations(Runtime))

  val pack = taskKey[File]("create a distributable package of the project")
  val packDir = settingKey[String]("pack-dir")

  val packBashTemplate = settingKey[String]("template file for bash scripts - defaults to pack's out-of-the-box template for bash")
  val packBatTemplate = settingKey[String]("template file for bash scripts - defaults to pack's out-of-the-box template for bat")
  val packMakeTemplate = settingKey[String]("template file for bash scripts - defaults to pack's out-of-the-box template for make")

  val packUpdateReports = taskKey[Seq[(sbt.UpdateReport, ProjectRef)]]("only for retrieving dependent module names")
  val packArchive = TaskKey[File]("pack-archive", "create a tar.gz archive of the distributable package")
  val packArchiveArtifact = SettingKey[Artifact]("tar.gz archive artifact")
  val packArchivePrefix = SettingKey[String]("prefix of (prefix)-(version).tar.gz archive file name")
  val packMain = TaskKey[Map[String, String]]("prog_name -> main class table")
  val packMainDiscovered = TaskKey[Map[String, String]]("discovered prog_name -> main class table")
  val packAllMainDiscovered = TaskKey[Map[String, String]]("all discovered prog_name -> main class table")
  val packExclude = SettingKey[Seq[String]]("pack-exclude", "specify projects to exclude when packaging")
  val packAllClasspaths = TaskKey[Seq[(Classpath, ProjectRef)]]("pack-all-classpaths")
  val packLibJars = TaskKey[Seq[(File, ProjectRef)]]("pack-lib-jars")
  val packGenerateWindowsBatFile = settingKey[Boolean]("Generate BAT file launch scripts for Windows")

  val packMacIconFile = SettingKey[String]("pack-mac-icon-file", "icon file name for Mac")
  val packResourceDir = SettingKey[Map[File, String]](s"pack-resource-dir", "pack resource directory. default = Map({projectRoot}/src/pack -> \"\")")
  val packAllUnmanagedJars = taskKey[Seq[(Classpath, ProjectRef)]]("all unmanaged jar files")
  val packJvmOpts = SettingKey[Map[String, Seq[String]]]("pack-jvm-opts")
  val packExtraClasspath = SettingKey[Map[String, Seq[String]]]("pack-extra-classpath")
  val packExpandedClasspath = settingKey[Boolean]("Expands the wildcard classpath in launch scripts to point at specific libraries")
  val packJarNameConvention = SettingKey[String]("pack-jarname-convention", "default: (artifact name)-(version).jar; original: original JAR name; full: (organization).(artifact name)-(version).jar; no-version: (organization).(artifact name).jar")
  val packDuplicateJarStrategy = SettingKey[String]("deal with duplicate jars. default to use latest version", "latest: use the jar with a higher version; exit: exit the task with error")

  lazy val packSettings = Seq[Def.Setting[_]](
    packDir := "pack",
    packBashTemplate := "/xerial/sbt/template/launch.mustache",
    packBatTemplate := "/xerial/sbt/template/launch-bat.mustache",
    packMakeTemplate := "/xerial/sbt/template/Makefile.mustache",
    packMain := Map.empty,
    packMainDiscovered := {
      def pascalCaseSplit(s: List[Char]): List[String] =
        if (s.isEmpty)
          Nil
        else if (!s.head.isUpper) {
          val (w, tail) = s.span(!_.isUpper)
          w.mkString :: pascalCaseSplit(tail)
        } else if (s.tail.headOption.forall(!_.isUpper)) {
          val (w, tail) = s.tail.span(!_.isUpper)
          (s.head :: w).mkString :: pascalCaseSplit(tail)
        } else {
          val (w, tail) = s.span(_.isUpper)
          w.init.mkString :: pascalCaseSplit(w.last :: tail)
        }

      def hyphenize(s: String): String =
        pascalCaseSplit(s.toList).map(_.toLowerCase).mkString("-")

      (discoveredMainClasses in Compile).value.map(mainClass => hyphenize(mainClass.split('.').last) -> mainClass).toMap
    },
    packAllMainDiscovered <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(packMainDiscovered) map { _.flatMap(_._1).toMap },
    packExclude := Seq.empty,
    packMacIconFile := "icon-mac.png",
    packResourceDir := Map(baseDirectory.value / "src/pack" -> ""),
    packJvmOpts := Map.empty,
    packExtraClasspath := Map.empty,
    packExpandedClasspath := false,
    packAllClasspaths <<= (thisProjectRef, buildStructure) flatMap getFromAllProjects(dependencyClasspath in Runtime),
    packAllUnmanagedJars <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(unmanagedJars in Runtime),
    packLibJars <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(packageBin in Runtime),
    packUpdateReports <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(update),
    packJarNameConvention := "default",
    packDuplicateJarStrategy := "latest",
    packGenerateWindowsBatFile := true,
    (mappings in pack) := Seq.empty,
    pack := {
      val dependentJars = collection.immutable.SortedMap.empty[ModuleEntry, File] ++ (
        for {
          (r: sbt.UpdateReport, projectRef) <- packUpdateReports.value
          c <- r.configurations if c.configuration == "runtime"
          m <- c.modules
          (artifact, file) <- m.artifacts if DependencyFilter.allPass(c.configuration, m.module, artifact)}
        yield {
          val mid = m.module
          val me = ModuleEntry(mid.organization, mid.name, VersionString(mid.revision), artifact.classifier, file.getName, projectRef)
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
      val libs: Seq[File] = packLibJars.value.map(_._1)
      out.log.info("project jars:\n" + libs.map(path => rpath(base, path)).mkString("\n"))
      libs.foreach(l => IO.copyFile(l, libDir / l.getName))

      // check duplicate jars
      val distinctDpJars = dependentJars.foldLeft(mutable.HashMap.empty[String, (ModuleEntry, File)])((result, jar) => {
        val key = jar._1.noVersionJarName
        if (result.contains(key)) {
          val old = result(key)
          val oldVersion = old._1.revision
          val newVersion = jar._1.revision
          if (oldVersion != newVersion) {
            if (packDuplicateJarStrategy.value == "exit")
              sys.error(s"Version conflict on ${key}: [${old._1.projectRef.project}] using $oldVersion V.S. [${jar._1.projectRef.project}] using $newVersion")

            out.log.warn(s"Version conflict on ${key}: [${old._1.projectRef.project}] using $oldVersion V.S. [${jar._1.projectRef.project}] using $newVersion")
            val latest = if (versionStringOrdering.compare(oldVersion, newVersion) > 0) old else jar
            out.log.warn(s"\tUsing the latest version ${latest._1.fullJarName}")
            result += (key -> latest)
          }
        }else {
          result += (key -> jar)
        }
        result
      }).map(_._2).toMap

      // Copy dependent jars
      def resolveJarName(m: ModuleEntry, convention: String) = {
        convention match {
          case "original" => m.originalFileName
          case "full" => m.fullJarName
          case "no-version" => m.noVersionJarName
          case _ => m.jarName
        }
      }

      out.log.info("project dependencies:\n" + distinctDpJars.keys.mkString("\n"))
      for ((m, f) <- distinctDpJars) {
        val targetFileName = resolveJarName(m, packJarNameConvention.value)
        IO.copyFile(f, libDir / targetFileName, true)
      }

      // Copy unmanaged jars in ${baseDir}/lib folder
      out.log.info("unmanaged dependencies:")
      for ((m, projectRef) <- packAllUnmanagedJars.value; um <- m; f = um.data) {
        out.log.info(f.getPath)
        IO.copyFile(f, libDir / f.getName, true)
      }

      // Copy explicitly added dependencies
      val mapped: Seq[(File, String)] = (mappings in pack).value
      out.log.info("explicit dependencies:")
      for ((file, path) <- mapped) {
        out.log.info(file.getPath)
        IO.copyFile(file, distDir / path, true)
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
        def expandedClasspath(sep: String): String = {
          val projJars = libs.map(l => "${PROG_HOME}/lib/" + l.getName)
          val depJars = distinctDpJars.keys.map("${PROG_HOME}/lib/" + resolveJarName(_, packJarNameConvention.value))
          val unmanagedJars = for ((m, projectRef) <- packAllUnmanagedJars.value; um <- m; f = um.data) yield "${PROG_HOME}/lib/" + f.getName
          (projJars ++ depJars ++ unmanagedJars).mkString("", sep, sep)
        }
        val expandedClasspathM = if (packExpandedClasspath.value) Map("EXPANDED_CLASSPATH" -> expandedClasspath(pathSeparator)) else Map()
        val m = Map(
          "PROG_NAME" -> name,
          "PROG_VERSION" -> progVersion,
          "MAIN_CLASS" -> mainClass,
          "MAC_ICON_FILE" -> packMacIconFile.value,
          "JVM_OPTS" -> packJvmOpts.value.getOrElse(name, Nil).map("\"%s\"".format(_)).mkString(" "),
          "EXTRA_CLASSPATH" -> extraClasspath(pathSeparator))
        val launchScript = engine.layout(packBashTemplate.value, m ++ expandedClasspathM)
        val progName = m("PROG_NAME").replaceAll(" ", "") // remove white spaces
        write(s"bin/$progName", launchScript)

        // Create BAT file
        if(packGenerateWindowsBatFile.value) {
          def replaceProgHome(s:String) = s.replaceAll("""\$\{PROG_HOME\}""", "%PROG_HOME%")

          val extraPath = extraClasspath("%PSEP%").replaceAll("/", """\\""")
          val expandedClasspathM = if (packExpandedClasspath.value) Map("EXPANDED_CLASSPATH" -> expandedClasspath("%PSEP%").replaceAll("/", """\\""")) else Map()
          val propForWin : Map[String, Any] = (m + ("EXTRA_CLASSPATH" -> extraPath) ++ expandedClasspathM).map{case (k, v) => k ->replaceProgHome(v)}.toMap
          val batScript = engine.layout(packBatTemplate.value, propForWin)
          write(s"bin/${progName}.bat", batScript)
        }
      }

      // Copy resources
      val otherResourceDirs = packResourceDir.value
      val binScriptsDir = otherResourceDirs.map(_._1 / "bin").filter(_.exists)
      out.log.info(s"packed resource directories = ${otherResourceDirs.map(_._1).mkString(",")}")

      def linkToScript(name: String) =
        "\t" + """ln -sf "../$(PROG)/current/bin/%s" "$(PREFIX)/bin/%s"""".format(name, name)

      // Create Makefile
      val makefile = {
        val additionalScripts = binScriptsDir.flatMap(_.listFiles).map(_.getName)
        val symlink = (mainTable.keys ++ additionalScripts).map(linkToScript).mkString("\n")
        val globalVar = Map("PROG_NAME" -> name.value, "PROG_SYMLINK" -> symlink)
        engine.layout(packMakeTemplate.value, globalVar)
      }
      write("Makefile", makefile)

      // Output the version number
      write("VERSION", "version:=" + progVersion + "\n")

      // Copy other scripts
      otherResourceDirs.foreach { otherResourceDir =>
        val from = otherResourceDir._1
        val to = otherResourceDir._2 match {
          case "" => distDir
          case p => distDir / p
        }
        IO.copyDirectory(from, to, overwrite=true, preserveLastModified=true)
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

  lazy val packAutoSettings = packSettings :+ (
    packMain := packAllMainDiscovered.value
  )


  def publishPackArchive : SettingsDefinition = {
    val pkgd = packagedArtifacts := packagedArtifacts.value updated (packArchiveArtifact.value, packArchive.value)
    Seq( artifacts += packArchiveArtifact.value, pkgd )
  }


  private def getFromAllProjects[T](targetTask: TaskKey[T])(currentProject: ProjectRef, structure: BuildStructure): Task[Seq[(T, ProjectRef)]] =
    getFromSelectedProjects(targetTask)(currentProject, structure, Seq.empty)


  private def getFromSelectedProjects[T](targetTask: TaskKey[T])(currentProject: ProjectRef, structure: BuildStructure, exclude: Seq[String]): Task[Seq[(T, ProjectRef)]] = {
    def allProjectRefs(currentProject: ProjectRef): Seq[ProjectRef] = {
      def isExcluded(p: ProjectRef) = exclude.contains(p.project)
      val children = Project.getProject(currentProject, structure).toSeq.flatMap {
        p =>
          p.uses
      }

      (currentProject +: (children flatMap (allProjectRefs(_)))) filterNot (isExcluded)
    }

    val projects: Seq[ProjectRef] = allProjectRefs(currentProject).distinct
    projects.map(p => (Def.task {((targetTask in p).value, p)}) evaluate structure.data).join
  }


  private def rpath(base: File, f: RichFile) = f.relativeTo(base).getOrElse(f).toString


}

