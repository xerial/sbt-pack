sbt-pack plugin
========

A sbt plugin for creating distributable Scala packages that include dependent jars and launch scripts.

### Features

- `sbt pack` creates a distributable package in `target/pack` folder.
  - All dependent jars including scala-library.jar are collected in `target/pack/lib` folder. This process is much faster than creating a single-jar as in `sbt-assembly` or `proguard` plugins. 
  - Supporting multi-module projects.
- `sbt pack-archive` generates `tar.gz` archive that is ready to distribute. 
  - The archive name is `target/{project name}-{version}.tar.gz`
- `sbt pack` generates program launch scripts `target/pack/bin/{program name}`
  - To run the program no need exists to install Scala, since it is included in the lib folder. Only java command needs to be found in the system.
  - It also generates `.bat` launch scripts for Windows users. 
- Generates a Makefile for program installation.
  - Do `cd target/pack; make install`. Then you can run your program with `~/local/bin/{program name}`
- You can install multiple versions of your program in the system.
  - The above Makefile script uses a separate folder for each version (e.g., `~/local/{project name}/{project version}`). 
  - The latest version is linked from `~/local/{project name}/current`
- You can add other resources in `src/pack` folder. 
  - All resources in this folder will be copied to `target/pack`.

* [Release Notes](ReleaseNotes.md)

### Usage

Add `sbt-pack` plugin to your sbt configuration:

**project/plugins.sbt**

```scala
addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.6.2")  // for sbt-0.13.x or higher

addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.2.5")  // for sbt-0.12.x (New features will not be supported in this version.)
```

Repository URL: http://repo1.maven.org/maven2/org/xerial/sbt/

#### Minimum configuration

**build.sbt**
```scala
packAutoSettings
```

Now you can use `sbt pack` command in your project.

#### Full build configuration

Import `xerial.sbt.Pack.packAutoSettings` into your project settings (Since version 0.6.2). sbt-pack finds main classes in your code and generates programs for them accordingly. The main classes must be Scala objects that define `def main(args:Array[])` method. The program names are the main classes names, hyphenized. (For example, main class `myprog.ExampleProg` gives program name `example-prog`.) 

Alternatively, import `xerial.sbt.Pack.packSettings` instead of `xerial.sbt.Pack.packAutoSettings`. The main classes in your program will then not be guessed. Manually set the `packMain` variable, a mapping from your program names to their corresponding main classes (for example `packMain := Map("hello" -> "myprog.Hello")`).   

**project/Build.scala**

```scala
import sbt._
import sbt.Keys._
import xerial.sbt.Pack._
   
object Build extends sbt.Build {
    
  lazy val root = Project(
    id = "myprog",
    base = file("."),
    settings = Defaults.defaultSettings 
      ++ packAutoSettings // This settings add pack and pack-archive commands to sbt
      ++ Seq(
        // [Optional] If you used packSettings instead of packAutoSettings, 
        //  specify mappings from program name -> Main class (full package path)
        // packMain := Map("hello" -> "myprog.Hello"),
        // Add custom settings here
        // [Optional] JVM options of scripts (program name -> Seq(JVM option, ...))
        packJvmOpts := Map("hello" -> Seq("-Xmx512m")),
        // [Optional] Extra class paths to look when launching a program. You can use ${PROG_HOME} to specify the base directory
        packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/etc")), 
        // [Optional] (Generate .bat files for Windows. The default value is true)
        packGenerateWindowsBatFile := true
        // [Optional] jar file name format in pack/lib folder (Since 0.5.0)
        //   "default"   (project name)-(version).jar 
        //   "full"      (organization name).(project name)-(version).jar
        //   "no-version" (organization name).(project name).jar
        //   "original"  (Preserve original jar file names)
        packJarNameConvention := "default",
        // [Optional] List full class paths in the launch scripts (default is false) (since 0.5.1)
        packExpandedClasspath := false,
        // [Optional] Resource directory mapping to be copied within target/pack. Default is Map("{projectRoot}/src/pack" -> "") 
        packResourceDir += (baseDirectory.value / "web" -> "web-content"),
      ) 
    // To publish tar.gz archive to the repository, add the following line (since 0.3.6)
    // ++ publishPackArchive  
    // Before 0.3.6, use below:
    // ++ addArtifact(Artifact("myprog", "arch", "tar.gz"), packArchive).settings
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

See also [examples](src/sbt-test/sbt-pack) folder
in the source code. It contains several Scala project examples using sbt-pack.

### Use case

- scala-min: A minimal Scala project using sbt-pack: <https://github.com/xerial/scala-min>
 - A minimal project to start writing Scala programs. 

	
### For developers

Creating IntelliJ project:

    $ ./sbt "gen-idea sbt-classifiers"

To test sbt-pack plugin, run

    $ ./sbt scripted

Run a single test project, e.g., `src/sbt-test/sbt-pack/multi-module`:

    $ ./sbt "scripted sbt-pack/multi-module"
