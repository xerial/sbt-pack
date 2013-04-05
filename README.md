sbt-pack plugin
========

A sbt plugin for creating distributable packages with dependent jars and launch scripts.

### Features

sbt-pack plugin do the following things:

- With `sbt pack` command, it creates a distributable package in `target/pack` folder.
- All dependent jars are collected in `target/pack/lib` folder. No need exists to create a single-jar as in `sbt-assembly` or `proguard` plugins. 
  - Support multi-module projects. 
- You can run your programs using a script in `target/pack/bin/{program name}`
- You can install your Scala programs to local machine `cd target/pack; make install`. Then you can run the command with `~/local/bin/{program name}`
- The above install Makefile script uses a separate folder for each program version (e.g., `~/local/{project name}/{project version}`), so you can have several versions of your program in a system. The latest one is linked from `~/local/{project name}/current`
- You can add other resources to be packed in `src/pack` folder. 

### Usage

Add `sbt-pack` plugin:

**project/plugins.sbt**

	addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.1.9")


Import `xerial.sbt.Pack.packSettings` into your project settings. Then set `packMain` variable, a mapping from the your program names to their corresponding main classes. The main classes must be Scala objects that define `def main(args:Array[])` method:

**project/Build.scala**

```scala
import sbt._
import sbt.Keys._
import xerial.sbt.Pack._
   
object Build extends sbt.Build {
    
  lazy val root = Project(
    id = "myprog",
    base = file("."),
    settings = Defaults.defaultSettings ++ packSettings ++
    Seq(
      // Map from program name -> Main class (full path)
      packMain := Map("hello" -> "myprog.Hello"),
      // Add custom settings here
      // JVM options of scripts (program name -> Seq(JVM option, ...))
      packJvmOpts := Map("hello" -> Seq("-Xmx512m")),
      // Extra class paths to look when launching a program
      packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/etc"))
    )
  )
}
```

**src/main/scala/Hello.scala**

```scala
package myprog
    
object Hello {
  def main(args:Array[String]) = {
    println("Hello World!!")
  }
}
```

**Create a package**

    $ sbt pack

Your program package will be generated in `target/pack` folder.

**Launch a command**

    $ target/pack/bin/hello
    Hello World!!

**Install the command**

    $ cd target/pack; make install
    $ ~/local/bin/hello
    Hello World!

**Install the command to the system**
   
    $ cd target/pack
    $ sudo make install PREFIX="/usr/local"
    $ /usr/local/bin/hello
    Hello World!

### Example projects

See also [examples](https://github.com/xerial/sbt-pack/tree/master/src/sbt-test/sbt-pack) folder
in the source code. It contains several Scala project examples using sbt-pack.

### Use case

- scala-min: A minimal Scala project using sbt-pack: <https://github.com/xerial/scala-min>
 - A minimal project to start writing Scala programs. 

	
