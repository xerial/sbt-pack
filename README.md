sbt-pack
========

A sbt plugin for creating distributable package with dependent jars and a launch script.

### Usage

Add `sbt-pack` plugin:
`project/plugins.sbt`

	addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.1-SNAPSHOT")


Import `xerial.sbt.Pack.packSettings` into your project settings. Then set `packMain` variable, a mapping from the your program names to their corresponding main classes. The main classes must be Scala objects that define `def main(args:Array[])` method:

`project/Build.scala`

    import sbt._
    import sbt.Keys._
    import xerial.sbt.Pack._
    
    object Build extends sbt.Build {
    
      lazy val root = Project(
        id = "example1",
        base = file("."),
        settings = Defaults.defaultSettings ++ packSettings ++
          Seq(
            // Map from program name -> Main class
            packMain := Map("hello" -> "Hello"),
            // custom settings here
            crossPaths := false,
			//libraryDependencies += ...
          )
      )
    }


`src/main/scala/Hello.scala`

    object Hello {
      def main(args:Array[String]) = {
        println("Hello World!!")
      }
    }


