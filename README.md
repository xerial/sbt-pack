sbt-pack plugin
========

A sbt plugin for creating distributable package with dependent jars and a launch script.

### Features

sbt-pack plugin do the following things:

- Create a distributable package in `target/pack` folder.
- Collect all dependent jars in `target/pack/lib` folder. No need exists to create a single-jar as in `sbt-assembly` or `proguard` plugins. 
- You can run your programs using a script in `target/pack/bin/{program name}`
- You can install your Scala programs to local machine `cd target/pack; make install`. Then you can run the command with `~/local/bin/{program name}`
- The above install Makefile script uses a separate folder for each program version (e.g., `~/local/{project name}/{project version}`), so you can have several versions of your program in a system. The latest one is linked from `~/local/{project name}/current`


### Usage

Add `sbt-pack` plugin:

**project/plugins.sbt**

	addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.1-SNAPSHOT")


Import `xerial.sbt.Pack.packSettings` into your project settings. Then set `packMain` variable, a mapping from the your program names to their corresponding main classes. The main classes must be Scala objects that define `def main(args:Array[])` method:

**project/Build.scala**

    import sbt._
    import sbt.Keys._
    import xerial.sbt.Pack._
    
    object Build extends sbt.Build {
    
      lazy val root = Project(
        id = "example1",
        base = file("."),
        settings = Defaults.defaultSettings ++ packSettings ++
          Seq(
            // Map from program name -> Main class (full path)
            packMain := Map("hello" -> "myprog.Hello"),
            // custom settings here
            crossPaths := false,
			//libraryDependencies += ...
          )
      )
    }


**src/main/scala/Hello.scala**


	package myprog
    
    object Hello {
      def main(args:Array[String]) = {
        println("Hello World!!")
      }
    }


**Create a package**

    $ sbt pack
	
Your program package will be generated in `target/pack` folder.

### Other examples of projects

See `examples` folder of this source code.

	
