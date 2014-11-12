//--------------------------------------
//
// VersionString.scala
// Since: 2014/11/10 9:15 AM
//
//--------------------------------------

package xerial.sbt

/**
 * Class to represent version strings
 * @author Christian Hoffmeister
 */
case class VersionString(numbers: List[Int], suffix: Option[String]) {
  def major = numbers.head
  def minor = numbers.drop(1).headOption
  def patch = numbers.drop(2).headOption

  override def toString = (numbers, suffix) match {
    case (n, Some(s)) if n.length > 0 => "%s-%s".format(n.mkString("."), s)
    case (n, None) if n.length > 0 => n.mkString(".")
    case (n, Some(s)) if n.length == 0 => s
    case _ => ""
  }
}

object VersionString {
  def apply(version: String): VersionString = {
    try {
      val numbers = version.split("\\-", 2).head.split("\\.", -1).map(_.toInt).toList
      val suffix = version.split("\\-", 2).tail.headOption
      VersionString(numbers, suffix)
    } catch {
      case _: Exception => VersionString(Nil, Some(version))
    }
  }
}

object DefaultVersionStringOrdering extends Ordering[VersionString] {
  override def compare(a: VersionString, b: VersionString): Int = {
    def compareNumberSequence(ns1: Seq[Int], ns2: Seq[Int]): Int = (ns1, ns2) match {
      case (Nil, Nil) => 0
      case (n1 :: tail1, Nil) => +1
      case (Nil, n2 :: tail2) => -1
      case (n1 :: tail1, n2 :: tail2) =>
        if (n1 < n2) -1
        else if (n1 > n2) +1
        else compareNumberSequence(tail1, tail2)
    }

    compareNumberSequence(a.numbers, b.numbers) match {
      case res if res != 0 => res
      case 0 =>
        (a.suffix, b.suffix) match {
          case (None, None) => 0
          case (Some(s1), None) => -1
          case (None, Some(s2)) => +1
          case (Some(s1), Some(s2)) =>
            if (s1 < s2) -1
            else if (s1 > s2) +1
            else 0
        }
    }
  }
}
