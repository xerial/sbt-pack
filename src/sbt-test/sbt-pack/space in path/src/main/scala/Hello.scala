package example

object Hello {

  def main(args: Array[String]) {
    println("Hello World!")

    // sbt-pack script sets prog.version and prog.home JVM options
    println(s"program version: ${sys.props("prog.version")}")
    println(s"program home: ${sys.props("prog.home")}")
  }

}
