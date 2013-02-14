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
  val packMain : SettingKey[Map[String, String]] = SettingKey[Map[String, String]]("packMain", "prog_name -> main class table")
  val packDir: SettingKey[String] = SettingKey[String]("pack-dir")
  val packExclude = SettingKey[Seq[String]]("pack-exclude")
  val packAllClasspaths = TaskKey[Seq[Classpath]]("pack-all-classpaths")
  val packDependencies = TaskKey[Seq[File]]("pack-dependencies")
  val packLibJars = TaskKey[Seq[File]]("pack-lib-jars")

  val packSettings = Seq[sbt.Project.Setting[_]](
    packDir := "pack",
    packMain := Map.empty,
    packExclude := Seq.empty,
    packAllClasspaths <<= (thisProjectRef, buildStructure) flatMap getFromAllProjects(dependencyClasspath.task in Runtime),
    packDependencies <<= packAllClasspaths.map {
      _.flatten.map(_.data).filter(ClasspathUtilities.isArchive).distinct
    },
    packLibJars <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(packageBin.task in Runtime)
  ) ++ Seq(packTask)

  private def getFromAllProjects[T](targetTask: SettingKey[Task[T]])(currentProject: ProjectRef, structure: Load.BuildStructure): Task[Seq[T]] =
    getFromSelectedProjects(targetTask)(currentProject, structure, Seq.empty)


  private def getFromSelectedProjects[T](targetTask: SettingKey[Task[T]])(currentProject: ProjectRef, structure: Load.BuildStructure, exclude: Seq[String]): Task[Seq[T]] = {
    def allProjectRefs(currentProject: ProjectRef): Seq[ProjectRef] = {
      def isExcluded(p:ProjectRef) = exclude.contains(p.project)
      val children = Project.getProject(currentProject, structure).toSeq.flatMap(_.aggregate)
      (currentProject +: (children flatMap ( allProjectRefs(_) ))) filterNot(isExcluded)
    }

    val projects: Seq[ProjectRef] = allProjectRefs(currentProject)
    projects.flatMap {
      targetTask in _ get structure.data
    } join
  }

  private def packTask = pack <<= (name, packMain, packDir, update, version, packLibJars, packDependencies, streams, target, dependencyClasspath in Runtime, classDirectory in Compile, baseDirectory) map {
      (name, mainTable, packDir, up, ver, libs, depJars, out, target, dependencies, classDirectory, base) => {

        def rpath(f:RichFile) = f.relativeTo(base) map { _.toString } getOrElse(f.toString)

        val distDir = target / packDir
        out.log.info("Creating a distributable package in " + rpath(distDir))
        IO.delete(distDir)
        distDir.mkdirs()


        val libDir = distDir / "lib"
        out.log.info("Copying libraries to " + rpath(libDir))
        libDir.mkdirs()
        (libs ++ depJars).foreach(l => IO.copyFile(l, libDir / l.getName))
        out.log.info("project jars:\n" + libs.mkString("\n"))
        out.log.info("project dependencies:\n" + depJars.mkString("\n"))

        val binDir = distDir / "bin"
        out.log.info("Create a bin folder: " + rpath(binDir))
        binDir.mkdirs()

        def read(path:String) : String = Resource.open(this.getClass, path) { f =>
          val b = new ByteArrayOutputStream
          val buf = new Array[Byte](8192)
          var ret = 0
          while({ ret = f.read(buf, 0, buf.length); ret != -1 }) {
            b.write(buf, 0, ret)
          }
          new String(b.toByteArray)
        }
        def write(path:String, content:String) {
          val p = distDir / path
          out.log.info("Generating %s".format(rpath(p)))
          IO.write(p, content)
        }

        // Create launch scripts
        out.log.info("Generating launch scripts")
        if(mainTable.isEmpty) {
          out.log.warn("No mapping (progran name) -> MainClass is defined. Please set packMain variable (Map[String, String]) in your sbt project settings.")
        }

        val mains = for((name, mainClass) <- mainTable) yield {
          out.log.info("main class for %s: %s".format(name, mainClass))
          Map[Any, String]("PROG_NAME"->name, "MAIN_CLASS"->mainClass)
        }

        for(m <- mains) {
          val launchScript = StringTemplate.eval(read("pack/script/launch.template"))(m)
          val progName = m("PROG_NAME").replaceAll(" ", "") // remove white spaces
          write("bin/%s".format(progName), launchScript)
        }


        // Create Makefile
        val globalVar = Map[Any, String]("PROG_NAME" -> name)
        val makefile = StringTemplate.eval(read("pack/script/Makefile.template"))(globalVar) +
          (for(p <- mainTable.keys) yield
            "\t" + """ln -sf "../$(PROG)/current/bin/%s" "$(PREFIX)/bin/%s"""".format(p, p)).mkString("\n") + "\n"

        val otherResourceDir = base / "src/pack"
        val binScriptsDir = otherResourceDir / "bin"
        val additionalLines : Array[String] = for(script <- Option(binScriptsDir.listFiles) getOrElse Array.empty) yield {
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

