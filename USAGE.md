# Detect duplicated classes in CLASSPATH

Sometimes, some jars include 3rd party dependencies embedded, or the same jar can be found under different organizations.  

If there are 2 different implementations of the same class in the same JVM, it is random which one will be used, and this can produce nasty bugs like it works in my computer but not in yours.

Use the `checkDuplicatedDependencies` task for detecting it.

It will fail and provide a list of all incompatibilities, in case it finds the same class in 2 different jars.

If the same class is duplicated, but it's the same implementation, it won't complain.

This is detected by computing a MD5 hash of the contents of the file.

If you consider the found conflicts are inoffensive, in order to ignore them in a future run, use the `checkDuplicateExclude` setting.  The value of this setting is automatically given.

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

If you consider these conflicts are inoffensive, in order to ignore them, use:
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

As suggested, the `checkDuplicatedExclude` setting can be used to ignore them.

###Implementation notes

Internally, it uses parallelism.  

It has been measured in a 4-core system and with a SSD disk, an improvement in time in a large app with 300 dependencies, from 229 seconds to 91 seconds. 

###Credits

[David Pérez Carmona](https://github.com/DavidPerezIngeniero)

