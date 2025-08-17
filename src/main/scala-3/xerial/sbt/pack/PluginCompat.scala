package xerial.sbt.pack

import java.nio.file.Path as NioPath
import sbt.*
import sbt.internal.inc.PlainVirtualFileConverter
import xsbti.{FileConverter, HashedVirtualFileRef, VirtualFile}
import sjsonnew.JsonFormat

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

  // JSON formats for sbt 2.0 compatibility - provide all needed instances
  import sjsonnew.{Builder, Unbuilder, JsonFormat}
  import sjsonnew.BasicJsonProtocol
  import sbt.internal.util.codec.JsonProtocol.{*, given}
  
  // Provide JsonFormat for FileRef
  given fileRefJsonFormat: JsonFormat[FileRef] = new JsonFormat[FileRef] {
    override def write[J](obj: FileRef, builder: Builder[J]): Unit = {
      val path = conv.toPath(obj).toString
      BasicJsonProtocol.StringJsonFormat.write(path, builder)
    }
    
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): FileRef = {
      val path = BasicJsonProtocol.StringJsonFormat.read(jsOpt, unbuilder)
      conv.toVirtualFile(NioPath.of(path))
    }
  }
  
  // Compatibility wrapper for sbt 2.0
  def uncached[T](value: => T): T = value
