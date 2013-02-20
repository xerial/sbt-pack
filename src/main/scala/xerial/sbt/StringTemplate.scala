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

  def eval(template: String)(properties: Map[String, String]) = new StringTemplate(template).eval(properties)

}


/**
 * @author leo
 */
class StringTemplate(template: String) {

  def eval(property: Map[String, String]): String = apply(property)

  def apply(property: Map[String, String]): String = {
    val p = property

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
        if (p.contains(key)) {
          out.append(p(key))
        }
        cursor = m.end
      }

      if (cursor < line.length)
        out.append(line.substring(cursor, line.length))
    }
    out.toString
  }

}