package envvars

object Test {
  def main(args: Array[String]) =
    Seq("key1" -> "value1", "key2" -> "value2").foreach { case (key, value) =>
      if (value == java.lang.System.getenv(key)) println(s"$key -> $value")
      else throw new RuntimeException("Assertion failed")
    }
}
