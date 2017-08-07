package xerial.sbt

import java.io._

import sbt._

package object pack {
  implicit class ArchiveFile(f: File) {
    def toList: List[String] = Option(f.getParentFile) match {
      case None => f.getName :: Nil
      case Some(p) => p.toList :+ f.getName
    }

    def toString(separator: String) =
      toList.mkString(separator)
  }

  def rpath(base: File, f: RichFile, separator: String = File.separator) =
    f.relativeTo(base).getOrElse(f.asFile).toString(separator)
}
