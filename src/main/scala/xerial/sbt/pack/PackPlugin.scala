//--------------------------------------
//
// Pack.scala
// Since: 2012/11/19 4:12 PM
//
//--------------------------------------

package xerial.sbt.pack

import java.io.{BufferedWriter, FileWriter}
import java.nio.file.Files
import java.time.format.{DateTimeFormatterBuilder, SignStyle}
import java.time.temporal.ChronoField._
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.{Date, Locale}

import sbt.Keys._
import sbt._

import scala.util.Try
import scala.util.matching.Regex

/** Plugin for packaging projects
  *
  * @author
  *   Taro L. Saito
  */
object PackPlugin extends AutoPlugin with PackArchive {

  override def trigger = noTrigger

  case class ModuleEntry(
      org: String,
      name: String,
      revision: VersionString,
      artifactName: String,
      classifier: Option[String],
      file: File
  ) {
    private def classifierSuffix = classifier.map("-" + _).getOrElse("")

    override def toString   = "%s:%s:%s%s".format(org, artifactName, revision, classifierSuffix)
    def originalFileName    = file.getName
    def jarName             = "%s-%s%s.jar".format(artifactName, revision, classifierSuffix)
    def fullJarName         = "%s.%s-%s%s.jar".format(org, artifactName, revision, classifierSuffix)
    def noVersionJarName    = "%s.%s%s.jar".format(org, artifactName, classifierSuffix)
    def noVersionModuleName = "%s.%s%s.jar".format(org, name, classifierSuffix)
    def toDependencyStr     = s""""${org}" % "${name}" % "${revision}""""
  }

  object autoImport {
    val pack          = taskKey[File]("create a distributable package of the project")
    val packInstall   = inputKey[Int]("pack and install")
    val packTargetDir = settingKey[File]("target directory to pack default is target")
    val packDir       = settingKey[String]("pack directory name")

    // val packBashTemplate = settingKey[String]("template file for bash scripts - defaults to pack's out-of-the-box template for bash")
    // val packBatTemplate  = settingKey[String]("template file for bash scripts - defaults to pack's out-of-the-box template for bat")
    // val packMakeTemplate = settingKey[String]("template file for bash scripts - defaults to pack's out-of-the-box template for make")

    val packMain           = taskKey[Map[String, String]]("prog_name -> main class table")
    val packMainDiscovered = taskKey[Map[String, String]]("discovered prog_name -> main class table")
    val packExclude = settingKey[Seq[String]]("specify projects whose dependencies will be excluded when packaging")
    val packExcludeLibJars =
      settingKey[Seq[String]]("specify projects to exclude when packaging.  Its dependencies will be processed")
    val packExcludeJars = settingKey[Seq[String]]("specify jar file name patterns to exclude when packaging")
    val packExcludeArtifactTypes =
      settingKey[Seq[String]]("specify artifact types (e.g. javadoc) to exclude when packaging")
    val packLibJars                = taskKey[Seq[(File, ProjectRef)]]("pack-lib-jars")
    val packGenerateWindowsBatFile = settingKey[Boolean]("Generate BAT file launch scripts for Windows")
    val packGenerateMakefile       = settingKey[Boolean]("Generate Makefile")

    val packMacIconFile = settingKey[String]("icon file name for Mac")
    val packResourceDir =
      settingKey[Map[File, String]]("pack resource directory. default = Map({projectRoot}/src/pack -> \"\")")
    val packAllUnmanagedJars = taskKey[Seq[(Classpath, ProjectRef)]]("all unmanaged jar files")
    val packModuleEntries    = taskKey[Seq[ModuleEntry]]("modules that will be packed")
    val packJvmOpts          = settingKey[Map[String, Seq[String]]]("pack-jvm-opts")
    val packExtraClasspath   = settingKey[Map[String, Seq[String]]]("pack-extra-classpath")
    val packExpandedClasspath =
      settingKey[Boolean]("Expands the wildcard classpath in launch scripts to point at specific libraries")
    val packJarNameConvention = settingKey[String](
      "default: (artifact name)-(version).jar; original: original JAR name; full: (organization).(artifact name)-(version).jar; no-version: (organization).(artifact name).jar"
    )
    val packDuplicateJarStrategy = settingKey[String]("""deal with duplicate jars. default to use latest version
        |latest: use the jar with a higher version; exit: exit the task with error""".stripMargin)
    val packCopyDependenciesTarget = settingKey[File]("target folder used by the <packCopyDependencies> task.")
    val packCopyDependencies = taskKey[Unit]("""just copies the dependencies to the <packCopyDependencies> folder.
        		|Compared to the <pack> task, it doesn't try to create scripts.
      	  """.stripMargin)
    val packCopyDependenciesUseSymbolicLinks =
      taskKey[Boolean]("""use symbolic links instead of copying for <packCopyDependencies>.
        		|The use of symbolic links allows faster processing and save disk space.
      	  """.stripMargin)

    val packArchivePrefix = settingKey[String]("prefix of (prefix)-(version).(format) archive file name")
    val packArchiveName   = settingKey[String]("archive file name. Default is (project-name)-(version)")
    val packArchiveStem   = settingKey[String]("directory name within the archive. Default is (archive-name)")
    val packJarListFile = settingKey[Option[String]](
      "jars list manifest file name, relative to packDir unless an absolute path. Default is None which means to not generate such a file"
    )
    val packArchiveExcludes    = settingKey[Seq[String]]("List of excluding files from the archive")
    val packArchiveTgzArtifact = settingKey[Artifact]("tar.gz archive artifact")
    val packArchiveTbzArtifact = settingKey[Artifact]("tar.bz2 archive artifact")
    val packArchiveTxzArtifact = settingKey[Artifact]("tar.xz archive artifact")
    val packArchiveZipArtifact = settingKey[Artifact]("zip archive artifact")
    val packArchiveTgz         = taskKey[File]("create a tar.gz archive of the distributable package")
    val packArchiveTbz         = taskKey[File]("create a tar.bz2 archive of the distributable package")
    val packArchiveTxz         = taskKey[File]("create a tar.xz archive of the distributable package")
    val packArchiveZip         = taskKey[File]("create a zip archive of the distributable package")
    val packArchive            = taskKey[Seq[File]]("create a tar.gz and a zip archive of the distributable package")
    val packEnvVars            = taskKey[Map[String, Map[String, String]]]("environment variables")
  }

  import complete.DefaultParsers._

  private val targetFolderParser: complete.Parser[Option[String]] =
    (Space ~> token(StringBasic, "(target folder)")).?.!!!("invalid input. please input target folder name")

  override lazy val projectSettings = packSettings ++ packArchiveSettings

  import autoImport._

  lazy val packSettings = Seq[Def.Setting[_]](
    packTargetDir := target.value,
    packDir       := "pack",
    // packBashTemplate := "/xerial/sbt/template/launch.mustache",
    // packBatTemplate := "/xerial/sbt/template/launch-bat.mustache",
    // packMakeTemplate := "/xerial/sbt/template/Makefile.mustache",
    packMain                   := packMainDiscovered.value,
    packExclude                := Seq.empty,
    packExcludeLibJars         := Seq.empty,
    packExcludeJars            := Seq.empty,
    packJarListFile            := None,
    packExcludeArtifactTypes   := Seq("source", "javadoc", "test"),
    packMacIconFile            := "icon-mac.png",
    packResourceDir            := Map(baseDirectory.value / "src/pack" -> ""),
    packJvmOpts                := Map.empty,
    packExtraClasspath         := Map.empty,
    packExpandedClasspath      := false,
    packJarNameConvention      := "default",
    packDuplicateJarStrategy   := "latest",
    packGenerateWindowsBatFile := true,
    packGenerateMakefile       := true,
    packMainDiscovered := Def.taskDyn {
      val mainClasses =
        getFromSelectedProjects(thisProjectRef.value, discoveredMainClasses in Compile, state.value, packExclude.value)
      Def.task {
        mainClasses.value.flatMap(_._1.map(mainClass => hyphenize(mainClass.split('.').last) -> mainClass).toMap).toMap
      }
    }.value,
    packAllUnmanagedJars := Def.taskDyn {
      val allUnmanagedJars =
        getFromSelectedProjects(thisProjectRef.value, unmanagedJars in Runtime, state.value, packExclude.value)
      Def.task { allUnmanagedJars.value }
    }.value,
    Def.derive(
      packLibJars := Def.taskDyn {
        def libJarsFromConfiguration(c: Configuration): Seq[Task[Seq[(File, ProjectRef)]]] =
          Seq(
            getFromSelectedProjects(
              thisProjectRef.value,
              c / packageBin,
              state.value,
              packExcludeLibJars.value
            )
          ) ++ c.extendsConfigs.flatMap(libJarsFromConfiguration)

        val libJars = libJarsFromConfiguration(configuration.value).join
        Def.task {
          libJars.value.flatten.distinct
        }
      }.value
    ),
    mappings := Seq.empty,
    Def.derive(
      packModuleEntries := {
        val out                          = streams.value
        val jarExcludeFilter: Seq[Regex] = packExcludeJars.value.map(_.r)
        def isExcludeJar(name: String): Boolean = {
          val toExclude = jarExcludeFilter.exists(pattern => pattern.findFirstIn(name).isDefined)
          if (toExclude) {
            out.log.info(s"Exclude $name from the package")
          }
          toExclude
        }

        val df = configurationFilter(name = configuration.value.name) // &&

        val dependentJars =
          for {
            c                <- update.value.filter(df).configurations
            m                <- c.modules if !m.evicted
            (artifact, file) <- m.artifacts
            if !packExcludeArtifactTypes.value.contains(artifact.`type`) && !isExcludeJar(file.name)
          } yield {
            val mid = m.module
            ModuleEntry(
              mid.organization,
              mid.name,
              VersionString(mid.revision),
              artifact.name,
              artifact.classifier,
              file
            )
          }

        implicit val versionStringOrdering = DefaultVersionStringOrdering
        val distinctDpJars = dependentJars
          .groupBy(_.noVersionModuleName)
          .flatMap {
            case (key, entries) if entries.groupBy(_.revision).size == 1 => entries
            case (key, entries) =>
              val revisions      = entries.groupBy(_.revision).map(_._1).toList.sorted
              val latestRevision = revisions.last
              packDuplicateJarStrategy.value match {
                case "latest" =>
                  out.log
                    .debug(s"Version conflict on $key. Using ${latestRevision} (found ${revisions.mkString(", ")})")
                  entries.filter(_.revision == latestRevision)
                case "exit" =>
                  sys.error(s"Version conflict on $key (found ${revisions.mkString(", ")})")
                case x =>
                  sys.error("Unknown duplicate JAR strategy '%s'".format(x))
              }
          }
        distinctDpJars.toSeq.distinct.sortBy(_.noVersionModuleName)
      }
    ),
    packCopyDependenciesUseSymbolicLinks := true,
    packCopyDependenciesTarget           := target.value / "lib",
    Def.derive(
      packCopyDependencies := {
        val log = streams.value.log

        val distinctDpJars   = packModuleEntries.value.map(_.file)
        val unmanaged        = packAllUnmanagedJars.value.flatMap { _._1 }.map { _.data }
        val copyDepTargetDir = packCopyDependenciesTarget.value
        val useSymlink       = packCopyDependenciesUseSymbolicLinks.value

        copyDepTargetDir.mkdirs()
        IO.delete((copyDepTargetDir * "*.jar").get)
        (distinctDpJars ++ unmanaged) foreach { d ⇒
          log debug s"Copying ${d.getName}"
          val dest = copyDepTargetDir / d.getName
          if (useSymlink) {
            Files.createSymbolicLink(dest.toPath, d.toPath)
          } else {
            IO.copyFile(d, dest)
          }
        }
        val libs = packLibJars.value.map(_._1)
        libs.foreach(l ⇒ IO.copyFile(l, copyDepTargetDir / l.getName))

        log info s"Copied ${distinctDpJars.size + libs.size} jars to ${copyDepTargetDir}"
      }
    ),
    packEnvVars := Map.empty,
    Def.derive(pack := {
      val out        = streams.value
      val logPrefix  = "[" + name.value + "] "
      val base: File = new File(".") // Using the working directory as base for readability

      val distDir: File = packTargetDir.value / packDir.value
      out.log.info(logPrefix + "Creating a distributable package in " + rpath(base, distDir))
      IO.delete(distDir)
      distDir.mkdirs()

      // Create target/pack/lib folder
      val libDir = distDir / "lib"
      libDir.mkdirs()

      // Copy project jars
      out.log.info(logPrefix + "Copying libraries to " + rpath(base, libDir))
      val libs: Seq[File] = packLibJars.value.map(_._1)
      out.log.info(logPrefix + "project jars:\n" + libs.map(path => rpath(base, path)).mkString("\n"))
      val projectJars = libs.map(l => {
        val dest = libDir / l.getName
        IO.copyFile(l, dest)
        dest
      })

      // Copy dependent jars

      val distinctDpJars = packModuleEntries.value
      out.log.info(logPrefix + "Copying project dependencies:")
      val jarNameConvention = packJarNameConvention.value
      val projectDepsJars = for (m <- distinctDpJars) yield {
        val targetFileName = resolveJarName(m, jarNameConvention)
        val dest           = libDir / targetFileName
        out.log.info(s"${m}")
        IO.copyFile(m.file, dest, true)
        dest
      }

      // Copy unmanaged jars in ${baseDir}/lib folder
      out.log.info(logPrefix + "Copying unmanaged dependencies:")
      val unmanagedDepsJars = for ((m, projectRef) <- packAllUnmanagedJars.value; um <- m; f = um.data) yield {
        out.log.info(f.getPath)
        val dest = libDir / f.getName
        IO.copyFile(f, dest, true)
        dest
      }

      // Copy explicitly added dependencies
      val mapped: Seq[(File, String)] = mappings.value
      out.log.info(logPrefix + "Copying explicit dependencies:")
      val explicitDepsJars = for ((file, path) <- mapped) yield {
        out.log.info(file.getPath)
        val dest = distDir / path
        IO.copyFile(file, dest, true)
        dest
      }

      if (packJarListFile.value.isDefined) {
        // put the list of jars in a file
        val jarListFileRelative = new File(packJarListFile.value.get)
        val jarListFile =
          if (jarListFileRelative.isAbsolute) jarListFileRelative else new File(distDir, packJarListFile.value.get)
        jarListFile.getParentFile.mkdirs()
        val bw = new BufferedWriter(new FileWriter(jarListFile))
        for (line <- projectJars ++ projectDepsJars ++ unmanagedDepsJars ++ explicitDepsJars) {
          bw.write(line.relativeTo(distDir).get.toString)
          bw.newLine()
        }
        bw.close()
      }

      // Create target/pack/bin folder
      val binDir = distDir / "bin"
      out.log.info(logPrefix + "Create a bin folder: " + rpath(base, binDir))
      binDir.mkdirs()

      def write(path: String, content: String) {
        val p = distDir / path
        out.log.info(logPrefix + "Generating %s".format(rpath(base, p)))
        IO.write(p, content)
      }

      // Create launch scripts
      out.log.info(logPrefix + "Generating launch scripts")
      val mainTable: Map[String, String] = packMain.value
      if (mainTable.isEmpty) {
        out.log.warn(
          logPrefix + "No mapping (program name) -> MainClass is defined. Please set packMain variable (Map[String, String]) in your sbt project settings."
        )
      }

      val progVersion = version.value
      val macIconFile = packMacIconFile.value

      // Check the current Git revision
      val gitRevision: String = Try {
        if ((base / ".git").exists()) {
          out.log.info(logPrefix + "Checking the git revision of the current project")
          sys.process.Process("git rev-parse HEAD").!!
        } else {
          "unknown"
        }
      }.getOrElse("unknown").trim

      val pathSeparator = "${PSEP}"
      val expandedCp    = packExpandedClasspath.value

      // Render script via Scalate template
      for ((name, mainClass) <- mainTable) {
        out.log.info(logPrefix + "main class for %s: %s".format(name, mainClass))
        def extraClasspath(sep: String): String =
          packExtraClasspath.value.get(name).map(_.mkString("", sep, sep)).getOrElse("")
        def expandedClasspath(sep: String): String = {
          val projJars = libs.map(l => "${PROG_HOME}/lib/" + l.getName)
          val depJars  = distinctDpJars.map(m => "${PROG_HOME}/lib/" + resolveJarName(m, jarNameConvention))
          val unmanagedJars = for ((m, projectRef) <- packAllUnmanagedJars.value; um <- m; f = um.data) yield {
            "${PROG_HOME}/lib/" + f.getName
          }
          (projJars ++ depJars ++ unmanagedJars).mkString("", sep, sep)
        }
        val expandedClasspathM = if (expandedCp) {
          Some(expandedClasspath(pathSeparator))
        } else {
          None
        }

        val scriptOpts = LaunchScript.Opts(
          PROG_NAME = name,
          PROG_VERSION = progVersion,
          PROG_REVISION = gitRevision,
          MAIN_CLASS = mainClass,
          JVM_OPTS = packJvmOpts.value.getOrElse(name, Nil).map("\"%s\"".format(_)).mkString(" "),
          EXTRA_CLASSPATH = extraClasspath(pathSeparator),
          MAC_ICON_FILE = macIconFile,
          ENV_VARS =
            packEnvVars.value.getOrElse(name, Map.empty).map { case (key, value) => s"$key=$value" }.mkString(" ")
        )

        // TODO use custom template (packBashTemplate)
        val launchScript = LaunchScript
          .generateLaunchScript(scriptOpts, expandedClasspathM)
          .replace("\n#!/bin/sh", "#!/bin/sh")
        val progName = name.replaceAll(" ", "") // remove white spaces
        write(s"bin/$progName", launchScript)

        // Create BAT file
        if (packGenerateWindowsBatFile.value) {
          def replaceProgHome(s: String) = s.replaceAll("""\$\{PROG_HOME\}""", "%PROG_HOME%")

          val extraPath = extraClasspath("%PSEP%").replaceAll("/", """\\""")
          val expandedClasspathM = if (expandedCp) {
            Some(replaceProgHome(expandedClasspath("%PSEP%").replaceAll("/", """\\""")))
          } else {
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
      val binScriptsDir     = otherResourceDirs.map(_._1 / "bin").filter(_.exists)
      out.log.info(logPrefix + s"packed resource directories = ${otherResourceDirs.map(_._1).mkString(",")}")

      def linkToScript(name: String) =
        "\t" + """ln -sf "../$(PROG)/current/bin/%s" "$(PREFIX)/bin/%s"""".format(name, name)

      val projectName = name.value
      if (packGenerateMakefile.value) {
        // Create Makefile
        // TODO Use custom template (packMakefileTemplate)
        val makefile = {
          val additionalScripts = binScriptsDir.flatMap(_.listFiles).map(_.getName)
          val symlink           = (mainTable.keys ++ additionalScripts).map(linkToScript).mkString("\n")
          LaunchScript.generateMakefile(projectName, symlink)
        }
        write("Makefile", makefile)
      }

      // Retrieve build time
      val systemZone = ZoneId.systemDefault().normalized()
      val timestamp  = ZonedDateTime.ofInstant(Instant.ofEpochMilli(new Date().getTime), systemZone)
      val buildTime  = humanReadableTimestampFormatter.format(timestamp)

      // Output the version number and Git revision
      write("VERSION", s"version:=${progVersion}\nrevision:=${gitRevision}\nbuildTime:=${buildTime}\n")

      // Copy other scripts
      otherResourceDirs.foreach { otherResourceDir =>
        val from = otherResourceDir._1
        val to = otherResourceDir._2 match {
          case "" => distDir
          case p  => distDir / p
        }
        IO.copyDirectory(from, to, overwrite = true, preserveLastModified = true)
      }

      // chmod +x the scripts in bin directory
      binDir.listFiles.foreach(_.setExecutable(true, false))

      out.log.info(logPrefix + "done.")
      distDir
    }),
    Def.derive(packInstall := {
      val arg: Option[String] = targetFolderParser.parsed
      val packDir             = pack.value
      val cmd = arg match {
        case Some(target) =>
          s"make install PREFIX=${target}"
        case None =>
          s"make install"
      }
      sys.process.Process(cmd, Some(packDir)).!
    })
  )

  private def getFromAllProjects[T](
      contextProject: ProjectRef,
      targetTask: TaskKey[T],
      state: State
  ): Task[Seq[(T, ProjectRef)]] =
    getFromSelectedProjects(contextProject, targetTask, state, Seq.empty)

  private def getFromSelectedProjects[T](
      contextProject: ProjectRef,
      targetTask: TaskKey[T],
      state: State,
      exclude: Seq[String]
  ): Task[Seq[(T, ProjectRef)]] = {
    val extracted = Project.extract(state)
    val structure = extracted.structure

    def transitiveDependencies(currentProject: ProjectRef): Seq[ProjectRef] = {
      def isExcluded(p: ProjectRef) = exclude.contains(p.project)

      def isCompileConfig(cp: ClasspathDep[ProjectRef]) = cp.configuration.forall(_.contains("compile->"))

      // Traverse all dependent projects
      val children = Project
        .getProject(currentProject, structure)
        .toSeq
        .flatMap { _.dependencies.filter(isCompileConfig).map(_.project) }

      (currentProject +: (children flatMap transitiveDependencies)) filterNot (isExcluded)
    }
    val projects: Seq[ProjectRef] = transitiveDependencies(contextProject).distinct
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
    } else if (!s.head.isUpper) {
      val (w, tail) = s.span(!_.isUpper)
      w.mkString :: pascalCaseSplit(tail)
    } else if (s.tail.headOption.forall(!_.isUpper)) {
      val (w, tail) = s.tail.span(!_.isUpper)
      (s.head :: w).mkString :: pascalCaseSplit(tail)
    } else {
      val (w, tail) = s.span(_.isUpper)
      w.init.mkString :: pascalCaseSplit(w.last :: tail)
    }

  private def hyphenize(s: String): String =
    pascalCaseSplit(s.toList).map(_.toLowerCase).mkString("-")

  private def resolveJarName(m: ModuleEntry, convention: String) = {
    convention match {
      case "original"   => m.originalFileName
      case "full"       => m.fullJarName
      case "no-version" => m.noVersionJarName
      case _            => m.jarName
    }
  }

}
