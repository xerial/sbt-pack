sbt-pack plugin [![Build Status](https://travis-ci.org/xerial/sbt-pack.svg?branch=master)](https://travis-ci.org/xerial/sbt-pack) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.xerial.sbt/sbt-pack/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.xerial.sbt/sbt-pack)
========

A sbt plugin for creating distributable Scala packages that include dependent jars and launch scripts.

### Features

- `sbt pack` creates a distributable package in `target/pack` folder.
  - All dependent jars including scala-library.jar are collected in `target/pack/lib` folder. This process is much faster than creating a single-jar as in `sbt-assembly` or `proguard` plugins. 
  - Supporting multi-module projects.
  - Useful for creating runnable [Docker](https://www.docker.com) images of Scala programs
- `sbt packArchive` generates `tar.gz` archive that is ready to distribute.
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
- Check duplicated classes in dependencies.  

* [Release Notes](ReleaseNotes.md)

### Usage

Add `sbt-pack` plugin to your sbt configuration:

**project/plugins.sbt**

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.xerial.sbt/sbt-pack/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.xerial.sbt/sbt-pack)

```scala
// for sbt-0.13.x, sbt-1.x
addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "(version)")  
```

Repository URL: http://repo1.maven.org/maven2/org/xerial/sbt/

#### Minimum configuration

**build.sbt**
```
// [Required] Enable plugin and automatically find def main(args:Array[String]) methods from the classpath
enablePlugins(PackPlugin)

// [Optional] Specify main classes manually
// This example creates `hello` command (target/pack/bin/hello) that calls org.mydomain.Hello#main(Array[String]) 
packMain := Map("hello" -> "org.mydomain.Hello")
```

Now you can use `sbt pack` command in your project.

#### Full build configuration

Import `xerial.sbt.Pack.packAutoSettings` into your project settings (Since version 0.6.2). sbt-pack finds main classes in your code and generates programs for them accordingly. The main classes must be Scala objects that define `def main(args:Array[])` method. The program names are the main classes names, hyphenized. (For example, main class `myprog.ExampleProg` gives program name `example-prog`.) 

Alternatively, import `xerial.sbt.Pack.packSettings` instead of `xerial.sbt.Pack.packAutoSettings`. The main classes in your program will then not be guessed. Manually set the `packMain` variable, a mapping from your program names to their corresponding main classes (for example `packMain := Map("hello" -> "myprog.Hello")`).   

**build.sbt**

```scala
// [Required] Enable plugin and automatically find def main(args:Array[String]) methods from the classpath
enablePlugins(PackPlugin)

name := "myprog"
base := file(".")
    
// [Optional] Specify mappings from program name -> Main class (full package path). If no value is set, it will find main classes automatically
packMain := Map("hello" -> "myprog.Hello")

// [Optional] JVM options of scripts (program name -> Seq(JVM option, ...))
packJvmOpts := Map("hello" -> Seq("-Xmx512m"))

// [Optional] Extra class paths to look when launching a program. You can use ${PROG_HOME} to specify the base directory
packExtraClasspath := Map("hello" -> Seq("${PROG_HOME}/etc")) 

// [Optional] (Generate .bat files for Windows. The default is true)
packGenerateWindowsBatFile := true

// [Optional] jar file name format in pack/lib folder
//   "default"   (project name)-(version).jar 
//   "full"      (organization name).(project name)-(version).jar
//   "no-version" (organization name).(project name).jar
//   "original"  (Preserve original jar file names)
packJarNameConvention := "default",

// [Optional] Patterns of jar file names to exclude in pack
packExcludeJars := Seq("scala-.*\\.jar")

// [Optional] List full class paths in the launch scripts (default is false) (since 0.5.1)
packExpandedClasspath := false
// [Optional] Resource directory mapping to be copied within target/pack. Default is Map("{projectRoot}/src/pack" -> "") 
packResourceDir += (baseDirectory.value / "web" -> "web-content")


// To publish tar.gz, zip archives to the repository, add the following lines:
import xerial.sbt.pack.PackPlugin._
publishPackArchives

// Publish only tar.gz archive. To publish another type of archive, use publishPackArchive(xxx) instead
//publishPackArchiveTgz
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

Install the command to `$(HOME)/local/bin`:
```
$ sbt packInstall
```

or

```
$ cd target/pack; make install
```

To launch the command:
```    
    $ ~/local/bin/hello
    Hello World!
```

Add the following configuration to your .bash_profile, .zsh_profile, etc. for the usability:
```
export PATH=$(HOME)/local/bin:$PATH
```

**Install the command to the system**

   
    $ cd target/pack
    $ sudo make install PREFIX="/usr/local"
    $ /usr/local/bin/hello
    Hello World!


**Create a tar.gz archive of your Scala program package**

    $ sbt packArchive

### Copy dependencies

The `packCopyDependencies` task copies all the dependencies to the folder specified through 
the `packCopyDependenciesTarget` setting.

By default, a symbolic link will be created.  By setting `packCopyDependenciesUseSymbolicLinks` to `false`, 
the files will be copied instead of symlinking.   A symbolic link is faster and uses less disk space.

It can be used e.g. for copying dependencies of a webapp to `WEB-INF/lib`

See an [example](src/sbt-test/sbt-pack/copy-dependencies) project.

### Example projects

See also [examples](src/sbt-test/sbt-pack) folder
in the source code. It contains several Scala project examples using sbt-pack.

### Use case

- scala-min: A minimal Scala project using sbt-pack: <https://github.com/xerial/scala-min>
 - A minimal project to start writing Scala programs. 


## Building A Docker image file with sbt-pack

Building a docker image of Scala application becomes easier with sbt-pack: 

**build.sbt**
```scala
enablePlugins(PackPlugin)
name := "myapp"
packMain := Map("myapp"->"org.yourdomain.MyApp")
```

**Dockerfile**
```
# Using an Alpine Linux based JDK image
FROM anapsix/alpine-java:8u131b11_jdk

COPY target/pack /srv/myapp

# Using a non-privileged user:
USER nobody
WORKDIR /srv/myapp

ENTRYPOINT ["sh", "./bin/myapp"]
```

Then you can build a docker image of your project:
```
$ sbt pack
$ docker build -t your_org/myapp:latest .


# Run your application with Docker
$ docker run -it --rm your_org/myapp:latest (command line arg...)
```

	
### For developers

To test sbt-pack plugin, run

    $ ./sbt scripted

Run a single test project, e.g., `src/sbt-test/sbt-pack/multi-module`:

    $ ./sbt "scripted sbt-pack/multi-module"

For releasing:

```
$ ./sbt
# cross tests for sbt 0.13 and 1.1
> ^ scripted
> ^ publishSigned
> sonatypeReleaseAll
```
