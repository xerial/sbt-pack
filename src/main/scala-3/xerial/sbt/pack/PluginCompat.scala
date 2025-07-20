package xerial.sbt.pack

import java.nio.file.Path as NioPath
import sbt.*
import sbt.internal.inc.PlainVirtualFileConverter
import xsbti.{FileConverter, HashedVirtualFileRef, VirtualFile}

// See https://www.eed3si9n.com/sbt-assembly-2.3.0
private[pack] object PluginCompat:
  type FileRef = HashedVirtualFileRef
  type Out     = VirtualFile

  given conv: FileConverter = PlainVirtualFileConverter.converter

  implicit def toFile(a: HashedVirtualFileRef): File = conv.toPath(a).toFile
  implicit def toFileRef(a: File): FileRef           = conv.toVirtualFile(a.toPath)

  def toNioPath(a: Attributed[HashedVirtualFileRef])(using conv: FileConverter): NioPath =
    conv.toPath(a.data)
  inline def toFile(a: Attributed[HashedVirtualFileRef])(using conv: FileConverter): File =
    toNioPath(a).toFile()
  def toNioPaths(cp: Seq[Attributed[HashedVirtualFileRef]])(using conv: FileConverter): Vector[NioPath] =
    cp.map(toNioPath).toVector
  inline def toFiles(cp: Seq[Attributed[HashedVirtualFileRef]])(using conv: FileConverter): Vector[File] =
    toNioPaths(cp).map(_.toFile())
