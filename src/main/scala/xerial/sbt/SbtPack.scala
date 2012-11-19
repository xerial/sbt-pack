//--------------------------------------
//
// SbtPack.scala
// Since: 2012/11/19 4:12 PM
//
//--------------------------------------

package xerial.sbt

import sbt._
import classpath.ClasspathUtilities
import Keys._

/**
 * Plugin for packaging projects
 * @author Taro L. Saito
 */
object SbtPack extends sbt.Plugin {

  val pack: TaskKey[File] = TaskKey[File]("pack", "create a distributable package of the project")
  val packDir: TaskKey[String] = TaskKey[String]("pack-dir")
  val packExclude = SettingKey[Seq[String]]("pack-exclude")
  val packAllClasspaths = TaskKey[Seq[Classpath]]("pack-all-classpaths")
  val packDependencies = TaskKey[Seq[File]]("pack-dependencies")
  val packLibJars = TaskKey[Seq[File]]("pack-lib-jars")

  val packSettings = Seq[sbt.Project.Setting[_]](
    packDir := "pack",
    packExclude := Seq.empty,
    packAllClasspaths <<= (thisProjectRef, buildStructure) flatMap getFromAllProjects(dependencyClasspath.task in Compile),
    packDependencies <<= packAllClasspaths.map {
      _.flatten.map(_.data).filter(ClasspathUtilities.isArchive).distinct
    },
    packLibJars <<= (thisProjectRef, buildStructure, packExclude) flatMap getFromSelectedProjects(packageBin.task in Compile),
    libraryDependencies ++= Seq("org.codehaus.plexus" % "plexus-classworlds" % "2.4" % "provided")
  ) ++ Seq(packTask)

  def getFromAllProjects[T](targetTask: SettingKey[Task[T]])(currentProject: ProjectRef, structure: Load.BuildStructure): Task[Seq[T]] =
    getFromSelectedProjects(targetTask)(currentProject, structure, Seq.empty)


  def getFromSelectedProjects[T](targetTask: SettingKey[Task[T]])(currentProject: ProjectRef, structure: Load.BuildStructure, exclude: Seq[String]): Task[Seq[T]] = {
    val projects: Seq[ProjectRef] = allProjectRefs(currentProject, structure, exclude)
    projects.flatMap {
      targetTask in _ get structure.data
    } join
  }

  def allProjectRefs(currentProject: ProjectRef, structure: Load.BuildStructure, exclude: Seq[String]): Seq[ProjectRef] = {
    def isExcluded(p:ProjectRef) = exclude.contains(p.project)
    val children = Project.getProject(currentProject, structure).toSeq.flatMap(_.aggregate)
    (currentProject +: (children flatMap { r => allProjectRefs(r, structure, exclude) })) filterNot(isExcluded)
  }

  def packTask = pack <<= (packDir, update, version, packLibJars, packDependencies, streams, target, dependencyClasspath in Runtime, classDirectory in Compile, baseDirectory) map {
      (packDir, up, ver, libs, depJars, out, target, dependencies, classDirectory, base) => {
        val distDir = target / packDir

        out.log.info("Create a distributable package in: " + distDir)
        IO.delete(distDir)
        distDir.mkdirs()

        out.log.info("project jars:\n" + libs.mkString("\n"))
        out.log.info("project dependencies:\n" + depJars.mkString("\n"))

        out.log.info("Copy libraries ...")
        val libDir = distDir / "lib"
        libDir.mkdirs()
        (libs ++ depJars).foreach(l => IO.copyFile(l, libDir / l.getName))

        out.log.info("Create a bin folder")
        val binDir = distDir / "bin"
        binDir.mkdirs()
        IO.copyDirectory(base / "src/script", binDir)
        // chmod +x
        if (!System.getProperty("os.name", "").contains("Windows")) {
          scala.sys.process.Process("chmod -R +x %s".format(binDir)).run
        }

        out.log.info("Generating VERSION file")
        IO.write(distDir / "VERSION", ver + "\n")
        out.log.info("done.")

        distDir
      }
    }


}

