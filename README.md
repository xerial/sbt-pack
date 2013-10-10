sbt-pack plugin
========

A sbt plugin for creating distributable Scala packages that include dependent jars and launch scripts.

### Features

- `sbt pack` creates a distributable package in `target/pack` folder.
  - All dependent jars including scala-library.jar are collected in `target/pack/lib` folder. This process is much faster than creating a single-jar as in `sbt-assembly` or `proguard` plugins. 
  - Supporting multi-module projects.
- `sbt pack-archive` generates `tar.gz` archive of your project.
  - `target/{program name}-{version}.tar.gz`.
- Generates program launch scripts `target/pack/bin/{program name}`
  - To run the program no need exists to install Scala, since it is included in the lib folder. Only java command needs to be found in the system.
- Generates a Makefile for program installation.
  - Do `cd target/pack; make install`. Then you can run your program with `~/local/bin/{program name}`
- You can install multiple versions of your program in the system.
  - The above Makefile script uses a separate folder for each version (e.g., `~/local/{project name}/{project version}`). 
  - The latest version is linked from `~/local/{project name}/current`
- You can add other resources in `src/pack` folder. 
  - All resources in this folder will be copied to `target/pack`.

### Release Notes
- August 28, 2013 - 0.3.1 release (only for sbt-0.13 or higher)
- August 21, 2013 - 0.2.4 release (for sbt-0.12.x)
  - Refine log messages
- August 20, 2013 - 0.2.1 release
  - Add a sample multi-module project
  - Fixes #11, #12
- May 16, 2013 - Version 0.2 release
  - Stable version
  - Delete only lib folder when `make install`
- May 9, 2013 - Version 0.1.10 release
  - Add pack-archive command

### Usage

Add `sbt-pack` plugin to your sbt configuration:

**project/plugins.sbt**

```scala
addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.3.1")  // for sbt-0.13.x or higher

addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.2.4")  // for sbt-0.12.x (will not be maintained)
```

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
      // Specify mappings from program name -> Main class (full package path)
      packMain := Map("hello" -> "myprog.Hello"),
      // Add custom settings here
      // [Optional] JVM options of scripts (program name -> Seq(JVM option, ...))
      packJvmOpts := Map("hello" -> Seq("-Xmx512m")),
      // [Optional] Extra class paths to look when launching a program
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

#### Command Examples

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


**Create a tar.gz archive of your Scala program package**

    $ sbt pack-archive

### Example projects

See also [examples](https://github.com/xerial/sbt-pack/tree/master/src/sbt-test/sbt-pack) folder
in the source code. It contains several Scala project examples using sbt-pack.

### Use case

- scala-min: A minimal Scala project using sbt-pack: <https://github.com/xerial/scala-min>
 - A minimal project to start writing Scala programs. 

	
### For developers

Creating IntelliJ project:

    $ bin/sbt "gen-idea sbt-classifiers"

To test sbt-pack plugin, run

    $ bin/sbt scripted

Run a single test project, e.g., `src/sbt-test/sbt-pack/multi-module`:

    $ bin/sbt "scripted sbt-pack/multi-module"
