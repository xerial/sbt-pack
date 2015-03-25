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
- Check duplicated classes in dependencies.  

* [Release Notes](ReleaseNotes.md)

### Usage

Add `sbt-pack` plugin to your sbt configuration:

**project/plugins.sbt**

```scala
addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.6.8")  // for sbt-0.13.x or higher

addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.2.5")  // for sbt-0.12.x (New features will not be supported in this version.)
```

Repository URL: http://repo1.maven.org/maven2/org/xerial/sbt/

#### Minimum configuration

**build.sbt**
```
// Automatically find def main(args:Array[String]) methods from classpath
packAutoSettings
```

or 
```
// If you need to specify main classes manually, use packSettings and packMain
packSettings

// [Optional] Creating `hello` command that calls org.mydomain.Hello#main(Array[String]) 
packMain := Map("hello" -> "org.mydomain.Hello")
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
        // [Optional] jar file name format in pack/lib folder
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
    // To publish tar.gz, zip archive etc. to the repository, add the following line
    // ++ publishPackArchive
    // If you need to publish tar.gz (publishTarGzArchive) only, use publishPack(xxx)Archive instead
    // ++ publishPackTarGzArchive
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

    $ sbt pack-archive

### Find duplicated classes in dependencies

Sometimes, some jars include 3rd party dependencies embedded, or the same jar can be found under different organizations.  

If there are 2 different implementations of the same class in the same JVM, it is random which one will be used, and this can produce nasty bugs like it works in my computer but not in yours.

Use the `checkDuplicatedDependencies` task for detecting it.

It will fail and provide a list of all incompatibilites, in case it finds the same class in 2 different jars.

If the same class is duplicated, but it's the same implementation, it won't complain.

This is detected by computing a MD5 hash of the contents of the file.

If you consider the found conflicts are inofensive, in order to ignore them in a future run, use the `checkDuplicateExclude` setting.  The value of this setting is automatically given.

Here is an example of report:
```
> checkDuplicatedDependencies
Conflict between org.slf4j:jcl-over-slf4j:1.7.10 and commons-logging:commons-logging:1.1.3:
  org/apache/commons/logging/impl/NoOpLog
  org/apache/commons/logging/impl/SimpleLog$1
  org/apache/commons/logging/impl/SimpleLog
  org/apache/commons/logging/Log
Conflict between commons-collections:commons-collections:3.2.1 and commons-beanutils:commons-beanutils-core:1.7.0:
  org/apache/commons/collections/ArrayStack
  org/apache/commons/collections/BufferUnderflowException
  org/apache/commons/collections/FastHashMap$1
Conflict between org.eclipse.birt.runtime:org.eclipse.birt.runtime:4.3.1 and commons-codec:commons-codec:1.6:
  org/apache/commons/codec/binary/Base32
  org/apache/commons/codec/binary/Base32InputStream
  org/apache/commons/codec/binary/Base32OutputStream
  org/apache/commons/codec/binary/Base64
  org/apache/commons/codec/binary/Base64InputStream
  org/apache/commons/codec/binary/Base64OutputStream
  org/apache/commons/codec/binary/BaseNCodec
Conflict between xerces:xercesImpl:2.9.1 and org.python:jython-standalone:2.5.2:
  org/w3c/dom/html/HTMLDOMImplementation
Conflict between commons-collections:commons-collections:3.2.1 and commons-beanutils:commons-beanutils:1.8.3:
  org/apache/commons/collections/ArrayStack
  org/apache/commons/collections/Buffer
  org/apache/commons/collections/BufferUnderflowException
  org/apache/commons/collections/FastHashMap$1
  org/apache/commons/collections/FastHashMap$CollectionView$CollectionViewIterator
Conflict between org.python:jython-standalone:2.5.2 and com.google.guava:guava:15.0:
  com/google/common/base/package-info
  com/google/common/collect/package-info
  com/google/common/io/package-info
  com/google/common/net/package-info
  com/google/common/primitives/package-info
  com/google/common/util/concurrent/package-info
Conflict between org.eclipse.birt.runtime:org.eclipse.osgi:3.9.1.v20130814-1242 and org.eclipse.birt.runtime:org.eclipse.osgi.services:3.3.100.v20130513-1956:
  org/osgi/service/log/LogService
  org/osgi/service/log/LogListener
  org/osgi/service/log/LogEntry
  org/osgi/service/log/LogReaderService
Conflict between commons-beanutils:commons-beanutils:1.8.3 and commons-beanutils:commons-beanutils-core:1.7.0:
  org/apache/commons/beanutils/BasicDynaBean
  org/apache/commons/beanutils/BasicDynaClass
  org/apache/commons/beanutils/BeanAccessLanguageException
Conflict between javax.mail:mail:1.4.1 and com.sun.mail:javax.mail:1.5.1:
  com/sun/mail/handlers/image_gif
  com/sun/mail/handlers/image_jpeg
  com/sun/mail/handlers/message_rfc822
  javax/mail/Address
  javax/mail/AuthenticationFailedException
  javax/mail/Authenticator
  javax/mail/BodyPart
  ...

If you consider these conflicts are inofensive, in order to ignore them, use:
set checkDuplicatedExclude := Seq(
  "org.slf4j" % "jcl-over-slf4j" % "1.7.10" -> "commons-logging" % "commons-logging" % "1.1.3",
  "commons-collections" % "commons-collections" % "3.2.1" -> "commons-beanutils" % "commons-beanutils-core" % "1.7.0",
  "org.eclipse.birt.runtime" % "org.eclipse.birt.runtime" % "4.3.1" -> "commons-codec" % "commons-codec" % "1.6",
  "xerces" % "xercesImpl" % "2.9.1" -> "org.python" % "jython-standalone" % "2.5.2",
  "commons-collections" % "commons-collections" % "3.2.1" -> "commons-beanutils" % "commons-beanutils" % "1.8.3",
  "org.python" % "jython-standalone" % "2.5.2" -> "com.google.guava" % "guava" % "15.0",
  "org.eclipse.birt.runtime" % "org.eclipse.osgi" % "3.9.1.v20130814-1242" -> "org.eclipse.birt.runtime" % "org.eclipse.osgi.services" % "3.3.100.v20130513-1956",
  "commons-beanutils" % "commons-beanutils" % "1.8.3" -> "commons-beanutils" % "commons-beanutils-core" % "1.7.0",
  "javax.mail" % "mail" % "1.4.1" -> "com.sun.mail" % "javax.mail" % "1.5.1"
)
                        
[error] (checkDuplicatedDependencies) Detected 424 conflict(s)
```

### Example projects

See also [examples](src/sbt-test/sbt-pack) folder
in the source code. It contains several Scala project examples using sbt-pack.

### Use case

- scala-min: A minimal Scala project using sbt-pack: <https://github.com/xerial/scala-min>
 - A minimal project to start writing Scala programs. 

	
### For developers

To test sbt-pack plugin, run

    $ ./sbt scripted

Run a single test project, e.g., `src/sbt-test/sbt-pack/multi-module`:

    $ ./sbt "scripted sbt-pack/multi-module"
