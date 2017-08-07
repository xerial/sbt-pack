//--------------------------------------
//
// Pack.scala
// Since: 2012/11/19 4:12 PM
//
//--------------------------------------

package xerial.sbt

import java.nio.file.Files
import java.time.format.{DateTimeFormatterBuilder, SignStyle}
import java.time.temporal.ChronoField._
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.{Date, Locale}

import sbt._
import sbt.Keys._
import xerial.sbt.PackPlugin.autoImport.{packMain, packMainDiscovered}

import scala.util.Try
import scala.util.matching.Regex

/**
  * Plugin for packaging projects
  *
  * @author Taro L. Saito
  */
object PackPlugin extends AutoPlugin with PackArchive {

  override def trigger = allRequirements

  case class ModuleEntry(org: String,
                         name: String,
                         revision: VersionString,
                         artifactName: String,
                         classifier: Option[String],
                         file: File) {
    private def classifierSuffix = classifier.map("-" + _).getOrElse("")

    override def toString = "%s:%s:%s%s".format(org, artifactName, revision, classifierSuffix)
    def originalFileName = file.getName
    def jarName = "%s-%s%s.jar".format(artifactName, revision, classifierSuffix)
    def fullJarName = "%s.%s-%s%s.jar".format(org, artifactName, revision, classifierSuffix)
    def noVersionJarName = "%s.%s%s.jar".format(org, artifactName, classifierSuffix)
    def noVersionModuleName = "%s.%s%s.jar".format(org, name, classifierSuffix)
    def toDependencyStr = s""""${org}" % "${name}" % "${revision}""""
  }


  object autoImport {
    val pack          = taskKey[File]("create a distributable package of the project")
    val packInstall   = inputKey[Int]("pack and install")
    val packTargetDir = settingKey[File]("target directory to pack default is target")
    val packDir       = settingKey[String]("pack directory name")

    val packBashTemplate = settingKey[String]("template file for bash scripts - defaults to pack's out-of-the-box template for bash")
    val packBatTemplate  = settingKey[String]("template file for bash scripts - defaults to pack's out-of-the-box template for bat")
    val packMakeTemplate = settingKey[String]("template file for bash scripts - defaults to pack's out-of-the-box template for make")

    val packMain                   = TaskKey[Map[String, String]]("prog_name -> main class table")
    val packMainDiscovered         = TaskKey[Map[String, String]]("discovered prog_name -> main class table")
    val packExclude                = SettingKey[Seq[String]]("pack-exclude", "specify projects whose dependencies will be excluded when packaging")
    val packExcludeLibJars         = SettingKey[Seq[String]]("pack-exclude", "specify projects to exclude when packaging.  Its dependencies will be processed")
    val packExcludeJars            = SettingKey[Seq[String]]("pack-exclude-jars", "specify jar file name patterns to exclude when packaging")
    val packExcludeArtifactTypes   = settingKey[Seq[String]]("specify artifact types (e.g. javadoc) to exclude when packaging")
    val packLibJars                = TaskKey[Seq[(File, ProjectRef)]]("pack-lib-jars")
    val packGenerateWindowsBatFile = settingKey[Boolean]("Generate BAT file launch scripts for Windows")

    val packMacIconFile                      = SettingKey[String]("pack-mac-icon-file", "icon file name for Mac")
    val packResourceDir                      = SettingKey[Map[File, String]](s"pack-resource-dir", "pack resource directory. default = Map({projectRoot}/src/pack -> \"\")")
    val packAllUnmanagedJars                 = taskKey[Seq[(Classpath, ProjectRef)]]("all unmanaged jar files")
    val packModuleEntries                    = taskKey[Seq[ModuleEntry]]("modules that will be packed")
    val packJvmOpts                          = SettingKey[Map[String, Seq[String]]]("pack-jvm-opts")
    val packExtraClasspath                   = SettingKey[Map[String, Seq[String]]]("pack-extra-classpath")
    val packExpandedClasspath                = settingKey[Boolean]("Expands the wildcard classpath in launch scripts to point at specific libraries")
    val packJarNameConvention                = SettingKey[String]("pack-jarname-convention",
      "default: (artifact name)-(version).jar; original: original JAR name; full: (organization).(artifact name)-(version).jar; no-version: (organization).(artifact name).jar")
    val packDuplicateJarStrategy             = SettingKey[String]("deal with duplicate jars. default to use latest version",
      "latest: use the jar with a higher version; exit: exit the task with error")
    val packCopyDependenciesTarget           = settingKey[File]("target folder used by the <packCopyDependencies> task.")
    val packCopyDependencies                 = taskKey[Unit](
      """just copies the dependencies to the <packCopyDependencies> folder.
        		|Compared to the <pack> task, it doesn't try to create scripts.
      	  """.stripMargin)
    val packCopyDependenciesUseSymbolicLinks = taskKey[Boolean](
      """use symbolic links instead of copying for <packCopyDependencies>.
        		|The use of symbolic links allows faster processing and save disk space.
      	  """.stripMargin)
  }

  import complete.DefaultParsers._

  private val targetFolderParser: complete.Parser[Option[String]] =
    (Space ~> token(StringBasic, "(target folder)")).?.!!!("invalid input. please input target folder name")

  override lazy val projectSettings = packSettings ++ packArchiveSettings

  import autoImport._

  lazy val packSettings = Seq[Def.Setting[_]](
    packTargetDir := target.value,
    packDir := "pack",
    packBashTemplate := "/xerial/sbt/template/launch.mustache",
    packBatTemplate := "/xerial/sbt/template/launch-bat.mustache",
    packMakeTemplate := "/xerial/sbt/template/Makefile.mustache",
    packMain := packMainDiscovered.value,
    packExclude := Seq.empty,
    packExcludeLibJars := Seq.empty,
    packExcludeJars := Seq.empty,
    packExcludeArtifactTypes := Seq("source", "javadoc", "test"),
    packMacIconFile := "icon-mac.png",
    packResourceDir := Map(baseDirectory.value / "src/pack" -> ""),
    packJvmOpts := Map.empty,
    packExtraClasspath := Map.empty,
    packExpandedClasspath := false,
    packJarNameConvention := "default",
    packDuplicateJarStrategy := "latest",
    packGenerateWindowsBatFile := true,
    packMainDiscovered := Def.taskDyn {
      val mainClasses = getFromSelectedProjects(discoveredMainClasses in Compile, state.value, packExclude.value)
      Def.task { mainClasses.value.flatMap(_._1.map(mainClass => hyphenize(mainClass.split('.').last) -> mainClass).toMap).toMap }
    }.value,
    packAllUnmanagedJars := Def.taskDyn {
      val allUnmanagedJars = getFromSelectedProjects(unmanagedJars in Runtime, state.value, packExclude.value)
      Def.task { allUnmanagedJars.value }
    }.value,
    packLibJars := Def.taskDyn {
      val libJars = getFromSelectedProjects(packageBin in Runtime, state.value, packExcludeLibJars.value)
      Def.task { libJars.value }
    }.value,
    (mappings in pack) := Seq.empty,
    packModuleEntries := {
      val out = streams.value
      val jarExcludeFilter: Seq[Regex] = packExcludeJars.value.map(_.r)
      def isExcludeJar(name: String): Boolean = {
        val toExclude = jarExcludeFilter.exists(pattern => pattern.findFirstIn(name).isDefined)
        if (toExclude) {
          out.log.info(s"Exclude $name from the package")
        }
        toExclude
      }

      val df = configurationFilter(name = "runtime") //&&

      val dependentJars =
        for {
          c <- update.value.filter(df).configurations
          m <- c.modules if !m.evicted
          (artifact, file) <- m.artifacts
          if !packExcludeArtifactTypes.value.contains(artifact.`type`) && !isExcludeJar(file.name)
        } yield {
          val mid = m.module
          ModuleEntry(mid.organization, mid.name, VersionString(mid.revision), artifact.name, artifact.classifier, file)
        }

      implicit val versionStringOrdering = DefaultVersionStringOrdering
      val distinctDpJars = dependentJars
                           .groupBy(_.noVersionModuleName)
                           .flatMap {
                             case (key, entries) if entries.groupBy(_.revision).size == 1 => entries
                             case (key, entries) =>
                               val revisions = entries.groupBy(_.revision).map(_._1).toList.sorted
                               val latestRevision = revisions.last
                               packDuplicateJarStrategy.value match {
                                 case "latest" =>
                                   out.log.debug(s"Version conflict on $key. Using ${latestRevision} (found ${revisions.mkString(", ")})")
                                   entries.filter(_.revision == latestRevision)
                                 case "exit" =>
                                   sys.error(s"Version conflict on $key (found ${revisions.mkString(", ")})")
                                 case x =>
                                   sys.error("Unknown duplicate JAR strategy '%s'".format(x))
                               }
                           }
      distinctDpJars.toSeq
    },
    packCopyDependenciesUseSymbolicLinks := true,
    packCopyDependenciesTarget := target.value / "lib",
    packCopyDependencies := {
      val log = streams.value.log

      val distinctDpJars = packModuleEntries.value.map(_.file)
      val unmanaged = packAllUnmanagedJars.value.flatMap {_._1}.map {_.data}
      val copyDepTargetDir = packCopyDependenciesTarget.value
      val useSymlink = packCopyDependenciesUseSymbolicLinks.value

      copyDepTargetDir.mkdirs()
      IO.delete((copyDepTargetDir * "*.jar").get)
      (distinctDpJars ++ unmanaged) foreach {d ⇒
        log debug s"Copying ${d.getName}"
        val dest = copyDepTargetDir / d.getName
        if (useSymlink) {
          Files.createSymbolicLink(dest.toPath, d.toPath)
        }
        else {
          IO.copyFile(d, dest)
        }
      }
      val libs = packLibJars.value.map(_._1)
      libs.foreach(l ⇒ IO.copyFile(l, copyDepTargetDir / l.getName))

      log info s"Copied ${distinctDpJars.size + libs.size} jars to ${copyDepTargetDir}"
    },
    pack := {
      val out = streams.value

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

      // Copy dependent jars

      val distinctDpJars = packModuleEntries.value
      out.log.info("project dependencies:\n" + distinctDpJars.mkString("\n"))
      val jarNameConvention = packJarNameConvention.value
      for (m <- distinctDpJars) {
        val targetFileName = resolveJarName(m, jarNameConvention)
        IO.copyFile(m.file, libDir / targetFileName, true)
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
      val macIconFile = packMacIconFile.value
      import sys.process._

      // Check the current Git revision
      val gitRevision: String = Try {
        out.log.info("Checking the git revison of the current project")
        sys.process.Process("git rev-parse HEAD").!!
      }.getOrElse("unknown").trim

      val pathSeparator = "${PSEP}"
      val expandedCp = packExpandedClasspath.value

      // Render script via Scalate template
      for ((name, mainClass) <- mainTable) {
        out.log.info("main class for %s: %s".format(name, mainClass))
        def extraClasspath(sep: String): String = packExtraClasspath.value.get(name).map(_.mkString("", sep, sep)).getOrElse("")
        def expandedClasspath(sep: String): String = {
          val projJars = libs.map(l => "${PROG_HOME}/lib/" + l.getName)
          val depJars = distinctDpJars.map(m => "${PROG_HOME}/lib/" + resolveJarName(m, jarNameConvention))
          val unmanagedJars = for ((m, projectRef) <- packAllUnmanagedJars.value; um <- m; f = um.data) yield {
            "${PROG_HOME}/lib/" + f.getName
          }
          (projJars ++ depJars ++ unmanagedJars).mkString("", sep, sep)
        }
        val expandedClasspathM = if(expandedCp) {
          Some(expandedClasspath(pathSeparator))
        }
        else {
          None
        }

        val scriptOpts = LaunchScript.Opts(
          PROG_NAME= name,
          PROG_VERSION = progVersion,
          PROG_REVISION = gitRevision,
          MAIN_CLASS =  mainClass,
          JVM_OPTS = packJvmOpts.value.getOrElse(name, Nil).map("\"%s\"".format(_)).mkString(" "),
          EXTRA_CLASSPATH = extraClasspath(pathSeparator),
          MAC_ICON_FILE = macIconFile
        )

        // TODO use custom template (packBashTemplate)
        val launchScript = LaunchScript.generateLaunchScript(scriptOpts, expandedClasspathM)
        val progName = name.replaceAll(" ", "") // remove white spaces
        write(s"bin/$progName", launchScript)

        // Create BAT file
        if (packGenerateWindowsBatFile.value) {
          def replaceProgHome(s: String) = s.replaceAll( """\$\{PROG_HOME\}""", "%PROG_HOME%")

          val extraPath = extraClasspath("%PSEP%").replaceAll("/", """\\""")
          val expandedClasspathM = if (expandedCp) {
            Some(replaceProgHome(expandedClasspath("%PSEP%").replaceAll("/", """\\""")))
          }
          else {
            None
          }
          // TODO use custom templte (packBatTemplate)
          val batScriptOpts = LaunchScript.Opts(
            PROG_NAME = name,
            PROG_VERSION = progVersion,
            PROG_REVISION = gitRevision,
            MAIN_CLASS = mainClass,
            JVM_OPTS = replaceProgHome(scriptOpts.JVM_OPTS),
            EXTRA_CLASSPATH = replaceProgHome(extraPath),
            MAC_ICON_FILE = replaceProgHome(macIconFile)
          )

          val batScript = LaunchScript.generateBatScript(batScriptOpts, expandedClasspathM)
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
      // TODO Use custom template (packMakefileTemplate)
      val makefile = {
        val additionalScripts = binScriptsDir.flatMap(_.listFiles).map(_.getName)
        val symlink = (mainTable.keys ++ additionalScripts).map(linkToScript).mkString("\n")
        LaunchScript.generateMakefile(name.value, symlink)
      }
      write("Makefile", makefile)

      // Retrieve build time
      val systemZone = ZoneId.systemDefault().normalized()
      val timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(new Date().getTime), systemZone)
      val buildTime = humanReadableTimestampFormatter.format(timestamp)

      // Output the version number and Git revision
      write("VERSION", s"version:=${progVersion}\nrevision:=${gitRevision}\nbuildTime:=${buildTime}\n")

      // Copy other scripts
      otherResourceDirs.foreach {otherResourceDir =>
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
    packInstall := {
      val arg: Option[String] = targetFolderParser.parsed
      val packDir = pack.value
      val cmd = arg match {
        case Some(target) =>
          s"make install PREFIX=${target}"
        case None =>
          s"make install"
      }
      sys.process.Process(cmd, Some(packDir)).!
    }
  )


  //private def getFromAllProjects[T](targetTask: TaskKey[T])(currentProject: ProjectRef, structure: BuildStructure): Task[Seq[(T, ProjectRef)]] =
  private def getFromAllProjects[T](targetTask: TaskKey[T], state:State): Task[Seq[(T, ProjectRef)]] =
    getFromSelectedProjects(targetTask, state, Seq.empty)

  //private def getFromSelectedProjects[T](targetTask: TaskKey[T])(currentProject: ProjectRef, structure: BuildStructure, exclude: Seq[String]): Task[Seq[(T, ProjectRef)]] = {
  private def getFromSelectedProjects[T](targetTask: TaskKey[T], state:State, exclude: Seq[String]): Task[Seq[(T, ProjectRef)]] = {
    val extracted = Project.extract(state)
    val structure = extracted.structure

    def allProjectRefs(currentProject: ProjectRef): Seq[ProjectRef] = {
      def isExcluded(p: ProjectRef) = exclude.contains(p.project)

      val children = Project.getProject(currentProject, structure).toSeq.flatMap(_.uses)
      (currentProject +: (children flatMap (allProjectRefs(_)))) filterNot (isExcluded)
    }
    val projects: Seq[ProjectRef] = allProjectRefs(extracted.currentRef).distinct
    projects.map(p => (Def.task { ((targetTask in p).value, p) }) evaluate structure.data).join
  }

  private val humanReadableTimestampFormatter = new DateTimeFormatterBuilder()
                                                .parseCaseInsensitive()
                                                .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                                                .appendLiteral('-')
                                                .appendValue(MONTH_OF_YEAR, 2)
                                                .appendLiteral('-')
                                                .appendValue(DAY_OF_MONTH, 2)
                                                .appendLiteral(' ')
                                                .appendValue(HOUR_OF_DAY, 2)
                                                .appendLiteral(':')
                                                .appendValue(MINUTE_OF_HOUR, 2)
                                                .appendLiteral(':')
                                                .appendValue(SECOND_OF_MINUTE, 2)
                                                .appendOffset("+HHMM", "Z")
                                                .toFormatter(Locale.US)

  private def pascalCaseSplit(s: List[Char]): List[String] =
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

  private def hyphenize(s: String): String =
    pascalCaseSplit(s.toList).map(_.toLowerCase).mkString("-")


  private def resolveJarName(m: ModuleEntry, convention: String) = {
    convention match {
      case "original" => m.originalFileName
      case "full" => m.fullJarName
      case "no-version" => m.noVersionJarName
      case _ => m.jarName
    }
  }

}
