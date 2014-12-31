package xerial

import _root_.sbt._
import java.io._

package object sbt {
  def rpath(base: File, f: RichFile) = f.relativeTo(base).getOrElse(f).toString
}
