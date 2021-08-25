package launcher

import xerial.core.log.Logger
import xerial.lens.cui._
import java.io.File
import io.Source

object Main {
  def main(args: Array[String]) {
    Launcher.of[Main].execute(args)
  }
}

class Main(
    @option(prefix = "-h,--help", description = "Display help messages", isHelp = true)
    help: Boolean = false
) extends DefaultCommand
    with Logger {

  def default = println(
    "Type --help for the list of commands.\nTo see the detailed help of each command, type (command name) --help"
  )

  @command(description = "Say hello")
  def hello = println("Hello World!")

  @command(description = "Repeat say hello")
  def repeat(
      @option(prefix = "-r", description = "Repeat hello")
      repeat: Int = 3
  ) {

    for (i <- 0 until repeat)
      hello
  }

  @command(description = "Show version information")
  def version {
    // Read VERSION file generated by sbt-pack plugin
    val home        = System.getProperty("prog.home")
    val versionFile = new File(home, "VERSION")
    val version =
      if (versionFile.exists())
        Source.fromFile(versionFile).getLines.toSeq.headOption
      else
        None

    println("launcher %s".format(version getOrElse "unknown"))
  }

  @command(description = "option setting examples")
  def complexCommands(
      @option(prefix = "--min", description = "min value")
      min: Int = 0,
      @option(prefix = "--max", description = "max value")
      max: Int = 10,
      @option(prefix = "-c", description = "card: spade|diamond|heard|clover")
      card: Option[Card] = None,
      @option(prefix = "-f", description = "flag")
      flag: Option[Boolean] = None,
      @argument
      otherArg: String
  ) {
    info(s"min:$min, max:$max")
    info(s"card:${card getOrElse ("no card is selected")}")
    info(s"flag:$flag")
    info(s"other arg:${otherArg}")
  }

}

/** Define your enum class
  */
object Card {

  object Spade   extends Card
  object Diamond extends Card
  object Heart   extends Card
  object Clover  extends Card

  val values = IndexedSeq(Spade, Diamond, Heart, Clover)

  lazy private val index = (values.map { c =>
    c.name.toLowerCase -> c
  }).toMap

  def unapply(s: String): Option[Card] = index.get(s.toLowerCase)
}

sealed abstract class Card {
  override def toString = name
  // retrieves enum name from the class name
  val name = {
    this.getClass.getSimpleName.replaceAll("\\$", "")
  }
}
