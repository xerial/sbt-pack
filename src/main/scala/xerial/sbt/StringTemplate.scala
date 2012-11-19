package xerial.sbt

//--------------------------------------
//
// StringTemplate.scala
// Since: 2012/11/20 0:17
//
//--------------------------------------

/**
 * @author Taro L. Saito
 */

object StringTemplate {

  def eval(template: String)(properties: Map[Any, String]) = new StringTemplate(template).eval(properties)


}


/**
 * @author leo
 */
class StringTemplate(template: String) {

  def eval(property: Map[Any, String]): String = apply(property)

  private def convert(properties: Map[Any, String]): Map[Symbol, String] = {
    (for ((k, v) <- properties) yield {
      k match {
        case s: Symbol => s -> v.toString
        case _ => Symbol(k.toString) -> v.toString
      }
    }).toMap
  }

  def apply(property: Map[Any, String]): String = {
    val p = convert(property)

    val pattern = """\{\{([^\}]+)\}\}""".r
    val out = new StringBuilder

    for ((line, lineCount) <- template.lines.zipWithIndex) {
      if (lineCount > 0)
        out.append("\n")

      var cursor = 0
      for (m <- pattern.findAllIn(line).matchData) {
        if (cursor < m.start) {
          out.append(line.substring(cursor, m.start))
        }
        val key = line.substring(m.start + 2, m.end-2)

        val k = Symbol(key)
        if (p.contains(k)) {
          out.append(p(k))
        }
        cursor = m.end;
      }

      if (cursor < line.length)
        out.append(line.substring(cursor, line.length))
    }
    out.toString
  }

}