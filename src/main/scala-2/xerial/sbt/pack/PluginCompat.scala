package xerial.sbt.pack

import java.nio.file.{Path => NioPath}
import sbt.*
import xsbti.FileConverter

private[pack] object PluginCompat {
  type FileRef = java.io.File
  type Out     = java.io.File

  implicit def toFile(a: FileRef): Out = a

  def toNioPath(a: Attributed[File])(implicit conv: FileConverter): NioPath =
    a.data.toPath()
  def toFile(a: Attributed[File])(implicit conv: FileConverter): File =
    a.data
  def toNioPaths(cp: Seq[Attributed[File]])(implicit conv: FileConverter): Vector[NioPath] =
    cp.map(_.data.toPath()).toVector
  def toFiles(cp: Seq[Attributed[File]])(implicit conv: FileConverter): Vector[File] =
    cp.map(_.data).toVector
  
  // Compatibility wrapper for sbt 1.x (no-op, just returns the value)
  def uncached[T](value: => T): T = value
  
  // Dummy for sbt 1.x (not needed but imported in common code)
  val fileRefJsonFormat = ()
}
