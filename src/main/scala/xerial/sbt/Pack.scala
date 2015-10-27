//--------------------------------------
//
// Pack.scala
// Since: 2012/11/19 4:12 PM
//
//--------------------------------------

package xerial.sbt

import java.io.InputStream
import java.nio.file.Files
import java.security.{DigestInputStream, MessageDigest}
import java.util.zip.ZipFile

import org.fusesource.scalate.TemplateEngine
import sbt.Keys._
import sbt._

import scala.util.matching.Regex

/**
 * Plugin for packaging projects
 * @author Taro L. Saito
 */
object Pack
        extends sbt.Plugin with PackArchive
{

  private case class ModuleEntry(org: String,
          name: String,
          revision: VersionString,
          artifactName: String,
          classifier: Option[String],
          originalFileName: String,
          projectRef: ProjectRef)
  {
    private def classifierSuffix = classifier.map("-" + _).getOrElse("")

    override def toString = "%s:%s:%s%s".format(org, artifactName, revision, classifierSuffix)

    def jarName = "%s-%s%s.jar".format(artifactName, revision, classifierSuffix)

    def fullJarName = "%s.%s-%s%s.jar".format(org, artifactName, revision, classifierSuffix)

    def noVersionJarName = "%s.%s%s.jar".format(org, artifactName, classifierSuffix)

    def noVersionModuleName = "%s.%s%s.jar".format(org, name, classifierSuffix)
  }

  private implicit def versionStringOrdering = DefaultVersionStringOrdering

  val runtimeFilter = ScopeFilter(inAnyProject, inConfigurations(Runtime))

  val pack = taskKey[File]("create a distributable package of the project")
  val packInstall = inputKey[Int]("pack and install")
  val packTargetDir = settingKey[File]("target directory to pack default is target")
  val packDir = settingKey[String]("pack directory name")

  val packBashTemplate = settingKey[String]("template file for bash scripts - defaults to pack's out-of-the-box template for bash")
  val packBatTemplate = settingKey[String]("template file for bash scripts - defaults to pack's out-of-the-box template for bat")
  val packMakeTemplate = settingKey[String]("template file for bash scripts - defaults to pack's out-of-the-box template for make")

  val packUpdateReports = taskKey[Seq[(sbt.UpdateReport, ProjectRef)]]("only for retrieving dependent module names")
  val packMain = TaskKey[Map[String, String]]("prog_name -> main class table")
  val packMainDiscovered = TaskKey[Map[String, String]]("discovered prog_name -> main class table")
  val packExclude = SettingKey[Seq[String]]("pack-exclude", "specify projects whose dependencies will be excluded when packaging")
  val packExcludeLibJars = SettingKey[Seq[String]]("pack-exclude", "specify projects to exclude when packaging.  Its dependencies will be processed")
  val packExcludeJars = SettingKey[Seq[String]]("pack-exclude-jars", "specify jar file name patterns to exclude when packaging")
  val packExcludeArtifactTypes = settingKey[Seq[String]]("specify artifact types (e.g. javadoc) to exclude when packaging")
  val packAllClasspaths = TaskKey[Seq[(Classpath, ProjectRef)]]("pack-all-classpaths")
  val packLibJars = TaskKey[Seq[(File, ProjectRef)]]("pack-lib-jars")
  val packGenerateWindowsBatFile = settingKey[Boolean]("Generate BAT file launch scripts for Windows")

  val packMacIconFile = SettingKey[String]("pack-mac-icon-file", "icon file name for Mac")
  val packResourceDir = SettingKey[Map[File, String]](s"pack-resource-dir", "pack resource directory. default = Map({projectRoot}/src/pack -> \"\")")
  val packAllUnmanagedJars = taskKey[Seq[(Classpath, ProjectRef)]]("all unmanaged jar files")
  val packJvmOpts = SettingKey[Map[String, Seq[String]]]("pack-jvm-opts")
  val packExtraClasspath = SettingKey[Map[String, Seq[String]]]("pack-extra-classpath")
  val packExpandedClasspath = settingKey[Boolean]("Expands the wildcard classpath in launch scripts to point at specific libraries")
  val packJarNameConvention = SettingKey[String]("pack-jarname-convention",
    "default: (artifact name)-(version).jar; original: original JAR name; full: (organization).(artifact name)-(version).jar; no-version: (organization).(artifact name).jar")
  val packDuplicateJarStrategy = SettingKey[String]("deal with duplicate jars. default to use latest version",
    "latest: use the jar with a higher version; exit: exit the task with error")
  val checkDuplicatedExclude = settingKey[Seq[(ModuleID, ModuleID)]]("list of pair of modules whose duplicated dependencies are ignored, because they are known to be harmless.")
  val checkDuplicatedDependencies = taskKey[Unit]("checks there are no duplicated dependencies, incompatible between them.")
  val packCopyDependenciesTarget = settingKey[File]("target folder used by the <packCopyDependencies> task.")
  val packCopyDependencies = taskKey[Unit](
	  """just copies the dependencies to the <packCopyDependencies> folder.
		|Compared to the <pack> task, it doesn't try to create scripts.
	  """.stripMargin)
  val packCopyDependenciesUseSymbolicLinks = taskKey[Boolean](
	  """use symbolic links instead of copying for <packCopyDependencies>.
		|The use of symbolic links allows faster processing and save disk space.
	  """.stripMargin)

  import complete.DefaultParsers._

  private val targetFolderParser: complete.Parser[Option[String]] =
    (Space ~> token(StringBasic, "(target folder)")).?.!!!("invalid input. please input target folder name")

  lazy val packSettings = Seq[Def.Setting[_]](
    packTargetDir := target.value,
    packDir := "pack",
    packBashTemplate := "/xerial/sbt/template/launch.mustache",
    packBatTemplate := "/xerial/sbt/template/launch-bat.mustache",
    packMakeTemplate := "/xerial/sbt/template/Makefile.mustache",
    packMain := Map.empty,
    packMainDiscovered <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(discoveredMainClasses in Compile) map {
      def pascalCaseSplit(s: List[Char]): List[String] =
        if (s.isEmpty) {
          Nil
        }
        else if (!s.head.isUpper) {
          val (w, tail) = s.span(!_.isUpper)
          w.mkString :: pascalCaseSplit(tail)
        }
        else if (s.tail.headOption.forall(!_.isUpper)) {
          val (w, tail) = s.tail.span(!_.isUpper)
          (s.head :: w).mkString :: pascalCaseSplit(tail)
        }
        else {
          val (w, tail) = s.span(_.isUpper)
          w.init.mkString :: pascalCaseSplit(w.last :: tail)
        }

      def hyphenize(s: String): String =
        pascalCaseSplit(s.toList).map(_.toLowerCase).mkString("-")

      allDiscoveredMainClasses =>
        allDiscoveredMainClasses.flatMap(_._1.map(mainClass => hyphenize(mainClass.split('.').last) -> mainClass).toMap).toMap
    },
    packExclude := Seq.empty,
    packExcludeLibJars := Seq.empty,
    packExcludeJars := Seq.empty,
    packExcludeArtifactTypes := Seq("source", "javadoc", "test"),
    packMacIconFile := "icon-mac.png",
    packResourceDir := Map(baseDirectory.value / "src/pack" -> ""),
    packJvmOpts := Map.empty,
    packExtraClasspath := Map.empty,
    packExpandedClasspath := false,
    packAllClasspaths <<= (thisProjectRef, buildStructure) flatMap getFromAllProjects(dependencyClasspath in Runtime),
    packAllUnmanagedJars <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(unmanagedJars in Runtime),
    packLibJars <<= (thisProjectRef, buildStructure, packExcludeLibJars) flatMap getFromSelectedProjects(packageBin in Runtime),
    packUpdateReports <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(update),
    packJarNameConvention := "default",
    packDuplicateJarStrategy := "latest",
    packGenerateWindowsBatFile := true,
    (mappings in pack) := Seq.empty,
    packInstall := {
      val arg: Option[String] = targetFolderParser.parsed
      val packDir = pack.value
      val cmd = arg match {
        case Some(target) =>
          s"make install PREFIX=${target}"
        case None =>
          s"make install"
      }
      Process(cmd, packDir).!
    },
    pack := {
      val out = streams.value

      val jarExcludeFilter : Seq[Regex] = packExcludeJars.value.map(_.r)
      def isExcludeJar(name:String): Boolean = {
        val toExclude = jarExcludeFilter.exists(pattern => pattern.findFirstIn(name).isDefined)
        if(toExclude) {
          out.log.info(s"Exclude $name from the package")
        }
        toExclude
      }

      val dependentJars =
        for {
          (r: sbt.UpdateReport, projectRef) <- packUpdateReports.value
          c <- r.configurations if c.configuration == "runtime"
          m <- c.modules
          (artifact, file) <- m.artifacts
          if !packExcludeArtifactTypes.value.contains(artifact.`type`) && !isExcludeJar(file.name)
        } yield {
          val mid = m.module
          val me = ModuleEntry(mid.organization, mid.name, VersionString(mid.revision), artifact.name, artifact.classifier, file.getName, projectRef)
          me -> file
        }

      val distDir: File = packTargetDir.value / packDir.value
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

      val distinctDpJars = dependentJars
              .groupBy(_._1.noVersionModuleName)
              .flatMap {
        case (key, entries) if entries.groupBy(_._1.revision).size == 1 => entries
        case (key, entries) =>
          val revisions = entries.groupBy(_._1.revision).map(_._1).toList.sorted
          val latestRevision = revisions.last
          packDuplicateJarStrategy.value match {
            case "latest" =>
              out.log.debug(s"Version conflict on $key. Using ${latestRevision} (found ${revisions.mkString(", ")})")
              entries.filter(_._1.revision == latestRevision)
            case "exit" =>
              sys.error(s"Version conflict on $key (found ${revisions.mkString(", ")})")
            case x =>
              sys.error("Unknown duplicate JAR strategy '%s'".format(x))
          }
      }
              .toMap

      // Copy dependent jars
      def resolveJarName(m: ModuleEntry, convention: String) =
      {
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

      def write(path: String, content: String)
      {
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
      val engine = new
                      TemplateEngine


      for ((name, mainClass) <- mainTable) {
        out.log.info("main class for %s: %s".format(name, mainClass))
        def extraClasspath(sep: String): String = packExtraClasspath.value.get(name).map(_.mkString("", sep, sep)).getOrElse("")
        def expandedClasspath(sep: String): String =
        {
          val projJars = libs.map(l => "${PROG_HOME}/lib/" + l.getName)
          val depJars = distinctDpJars.keys.map("${PROG_HOME}/lib/" + resolveJarName(_, packJarNameConvention.value))
          val unmanagedJars = for ((m, projectRef) <- packAllUnmanagedJars.value; um <- m; f = um.data) yield {
            "${PROG_HOME}/lib/" + f.getName
          }
          (projJars ++ depJars ++ unmanagedJars).mkString("", sep, sep)
        }
        val expandedClasspathM = if (packExpandedClasspath.value) {
          Map("EXPANDED_CLASSPATH" -> expandedClasspath(pathSeparator))
        }
        else {
          Map()
        }
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
        if (packGenerateWindowsBatFile.value) {
          def replaceProgHome(s: String) = s.replaceAll( """\$\{PROG_HOME\}""", "%PROG_HOME%")

          val extraPath = extraClasspath("%PSEP%").replaceAll("/", """\\""")
          val expandedClasspathM = if (packExpandedClasspath.value) {
            Map("EXPANDED_CLASSPATH" -> expandedClasspath("%PSEP%").replaceAll("/", """\\"""))
          }
          else {
            Map()
          }
          val propForWin: Map[String, Any] = (m + ("EXTRA_CLASSPATH" -> extraPath) ++ expandedClasspathM).map { case (k, v) => k -> replaceProgHome(v) }.toMap
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
        IO.copyDirectory(from, to, overwrite = true, preserveLastModified = true)
      }

      // chmod +x the scripts in bin directory
      binDir.listFiles.foreach(_.setExecutable(true, false))

      out.log.info("done.")
      distDir
    },

    checkDuplicatedExclude := Seq.empty,

    checkDuplicatedDependencies := {
      val log = streams.value.log

      val dependentJars =
        for {
          (r: sbt.UpdateReport, projectRef) <- packUpdateReports.value
          c <- r.configurations if c.configuration == "runtime"
          m <- c.modules
          (artifact, file) <- m.artifacts if !packExcludeArtifactTypes.value.contains(artifact.`type`)
        } yield {
          val mid = m.module
          val me = ModuleEntry(mid.organization, mid.name, VersionString(mid.revision), artifact.name, artifact.classifier, file.getName, projectRef)
          me -> file
        }

      def hash(is: InputStream) = {
        val md = MessageDigest.getInstance("MD5")
        val dis = new DigestInputStream(is, md)
        Iterator.continually(dis.read()).takeWhile(_ >= 0).foreach{_ ⇒ ()}
        md.digest()
      }

      def modID(m: ModuleEntry) = m.org % m.artifactName % m.revision.toString

      val distinctDpJars = dependentJars
          .groupBy(_._1.noVersionModuleName)
          .map {
            case (key, entries) if entries.groupBy(_._1.revision).size == 1 ⇒
              val e0 = entries(0)
              (modID(e0._1), e0._2)
            case (key, entries) ⇒
              val revisions = entries.groupBy(_._1.revision).map(_._1).toList.sorted
              val latestRevision = revisions.last
              packDuplicateJarStrategy.value match {
                case "latest" =>
                  log.debug(s"Version conflict on $key. Using ${latestRevision} (found ${revisions.mkString(", ")})")
                  val entry = entries.filter(_._1.revision == latestRevision)(0)
                  (modID(entry._1), entry._2)
                case "exit" =>
                  sys.error(s"Version conflict on $key (found ${revisions.mkString(", ")})")
                case x =>
                  sys.error("Unknown duplicate JAR strategy '%s'".format(x))
              }
          }

      val allClasses = distinctDpJars.map { case (mod, file) ⇒
        import scala.collection.JavaConversions._
        log debug s"Scanning $file"
        val jar = new ZipFile(file)
        val classes = try {
          jar.entries
              .filter { e ⇒ !e.isDirectory && e.getName.endsWith(".class") }
              .toList
              .map { e ⇒
                val h = hash(jar.getInputStream(e))
                //log debug s"${e.getName} ⇒ ${h.map(a ⇒ f"$a%02X").mkString}"
                (e.getName, h)
              }
        } finally
          jar.close()
        (mod, classes)
      }

      val conflicts = for {
        ((mod1, hashes1), index) ← allClasses.zipWithIndex
        others = allClasses.seq.view(index+1, allClasses.size).par
        (file1, hash1) ← hashes1
        (mod2, hashes2) ← others
        if !checkDuplicatedExclude.value.exists{ case (m1, m2) ⇒
          m1 == mod1 && m2 == mod2 || m2 == mod1 && m1 == mod2
        }
        (file2, hash2) ← hashes2
        if file1 == file2 && !(hash1 sameElements hash2)
      } yield {
          //log debug mod+" "+mod2+" "+file1
          (mod1, mod2, file1)
      }

      if (conflicts.size > 0) {
        val groupedConflicts = conflicts.groupBy { case (mod1, mod2, file) ⇒
          (mod1, mod2)
        }.mapValues { _.map{ case (mod1, mod2, file) ⇒ file } }
        groupedConflicts.foreach { case ((m1, m2), files) ⇒
          val f = files.map{ "\n  "+_.replaceFirst(".class$", "")}.mkString
          println(s"Conflict between $m1 and $m2:"+f)
        }

        def toStr(m: ModuleID) = s""""${m.organization}" % "${m.name}" % "${m.revision}""""
        val excludes = groupedConflicts.map{ case ((m1, m2), _) ⇒ s"  ${toStr(m1)} -> ${toStr(m2)}" }.mkString(",\n")

        println(s"""
			  |If you consider these conflicts are inoffensive, in order to ignore them, use:
			  |set checkDuplicatedExclude := Seq(
			  |$excludes
			  |)
			""".stripMargin)
        sys.error(s"Detected ${conflicts.size} conflict(s)")
      } else
        log info s"No conflicts detected, scanned ${dependentJars.size} jar files."
    },

    packCopyDependenciesUseSymbolicLinks := true,
    packCopyDependenciesTarget := target.value / "lib",

    packCopyDependencies := {
      val log = streams.value.log

      val jarExcludeFilter : Seq[Regex] = packExcludeJars.value.map(_.r)
      def isExcludeJar(name:String): Boolean = {
        val toExclude = jarExcludeFilter.exists(pattern => pattern.findFirstIn(name).isDefined)
        if(toExclude) {
          log.info(s"Exclude $name from the package")
        }
        toExclude
      }

      val dependentJars =
        for {
          (r: sbt.UpdateReport, projectRef) <- packUpdateReports.value
          c <- r.configurations if c.configuration == "runtime"
          m <- c.modules
          (artifact, file) <- m.artifacts if !packExcludeArtifactTypes.value.contains(artifact.`type`) && !isExcludeJar(file.name)
        } yield {
          val mid = m.module
          val me = ModuleEntry(mid.organization, mid.name, VersionString(mid.revision), artifact.name, artifact.classifier, file.getName, projectRef)
          me -> file
        }

      val distinctDpJars = dependentJars
          .groupBy(_._1.noVersionModuleName)
          .map {
            case (key, entries) if entries.groupBy(_._1.revision).size == 1 ⇒
              val e0 = entries(0)
              e0._2
            case (key, entries) ⇒
              val revisions = entries.groupBy(_._1.revision).map(_._1).toList.sorted
              val latestRevision = revisions.last
              packDuplicateJarStrategy.value match {
                case "latest" =>
                  log.debug(s"Version conflict on $key. Using ${latestRevision} (found ${revisions.mkString(", ")})")
                  val entry = entries.filter(_._1.revision == latestRevision)(0)
                  entry._2
                case "exit" =>
                  sys.error(s"Version conflict on $key (found ${revisions.mkString(", ")})")
                case x =>
                  sys.error("Unknown duplicate JAR strategy '%s'".format(x))
              }
          }

      val unmanaged = packAllUnmanagedJars.value.flatMap{_._1}.map{_.data}
      packCopyDependenciesTarget.value.mkdirs()
      IO.delete((packCopyDependenciesTarget.value * "*.jar").get)
      (distinctDpJars ++ unmanaged) foreach { d ⇒
        log debug s"Copying ${d.getName}"
        val dest = packCopyDependenciesTarget.value / d.getName
        if (packCopyDependenciesUseSymbolicLinks.value)
          Files.createSymbolicLink(dest.toPath, d.toPath)
        else
          IO.copyFile(d, dest)
      }
      val libs = packLibJars.value.map(_._1)
      libs.foreach(l ⇒ IO.copyFile(l, packCopyDependenciesTarget.value / l.getName))

      log info s"Copied ${distinctDpJars.size+libs.size} jars to ${packCopyDependenciesTarget.value}"
    }
  ) ++ packArchiveSettings

  lazy val packAutoSettings = packSettings :+ (
          packMain := packMainDiscovered.value
          )

  private def getFromAllProjects[T](targetTask: TaskKey[T])(currentProject: ProjectRef, structure: BuildStructure): Task[Seq[(T, ProjectRef)]] =
    getFromSelectedProjects(targetTask)(currentProject, structure, Seq.empty)

  private def getFromSelectedProjects[T](targetTask: TaskKey[T])(currentProject: ProjectRef, structure: BuildStructure, exclude: Seq[String]): Task[Seq[(T, ProjectRef)]] =
  {
    def allProjectRefs(currentProject: ProjectRef): Seq[ProjectRef] =
    {
      def isExcluded(p: ProjectRef) = exclude.contains(p.project)
      val children = Project.getProject(currentProject, structure).toSeq.flatMap {
        p =>
          p.uses
      }

      (currentProject +: (children flatMap (allProjectRefs(_)))) filterNot (isExcluded)
    }

    val projects: Seq[ProjectRef] = allProjectRefs(currentProject).distinct
    projects.map(p => (Def.task {
      ((targetTask in p).value, p)
    }) evaluate structure.data).join
  }
}
