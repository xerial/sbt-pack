package xerial.sbt

import sbt._
import Keys._
import java.io._
import org.apache.commons.compress.archivers._
import org.apache.commons.compress.archivers.tar._
import org.apache.commons.compress.archivers.zip._
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.utils.IOUtils
import java.nio.file.Files
import scala.collection.JavaConversions._

trait PackArchive {
  val packArchivePrefix = SettingKey[String]("prefix of (prefix)-(version).(format) archive file name")
  val packArchiveName = SettingKey[String]("archive file name. Default is (project-name)-(version)")
  val packArchiveExcludes = SettingKey[Seq[String]]("List of excluding files from the archive")
  val packArchivePosixMask = SettingKey[File => Option[Int]]("Function that associates an archive mask with a given path")
  val packArchiveTgzArtifact = SettingKey[Artifact]("tar.gz archive artifact")
  val packArchiveTbzArtifact = SettingKey[Artifact]("tar.bz2 archive artifact")
  val packArchiveTxzArtifact = SettingKey[Artifact]("tar.xz archive artifact")
  val packArchiveZipArtifact = SettingKey[Artifact]("zip archive artifact")
  val packArchiveTgz = TaskKey[File]("pack-archive-tgz", "create a tar.gz archive of the distributable package")
  val packArchiveTbz = TaskKey[File]("pack-archive-tbz", "create a tar.bz2 archive of the distributable package")
  val packArchiveTxz = TaskKey[File]("pack-archive-txz", "create a tar.xz archive of the distributable package")
  val packArchiveZip = TaskKey[File]("pack-archive-zip", "create a zip archive of the distributable package")
  val packArchive = TaskKey[Seq[File]]("pack-archive", "create a tar.gz and a zip archive of the distributable package")

  private def createArchive(
    archiveSuffix: String,
    createOutputStream: (OutputStream) => ArchiveOutputStream,
    createEntry: (File, String, File => Option[Int]) => ArchiveEntry) = Def.task {
    val posixMaskFunction = packArchivePosixMask.value
    val out = streams.value
    val targetDir: File = Pack.packTargetDir.value
    val distDir: File = Pack.pack.value // run pack command here
    val archiveStem = s"${packArchiveName.value}"
    val archiveName = s"${archiveStem}.${archiveSuffix}"
    out.log.info("Generating " + rpath(baseDirectory.value, targetDir / archiveName))
    val aos = createOutputStream(new BufferedOutputStream(new FileOutputStream(targetDir / archiveName)))
    val excludeFiles = packArchiveExcludes.value.toSet
    def addFilesToArchive(dir: File): Unit = Option(dir.listFiles)
            .getOrElse(Array.empty)
            .filterNot(f => excludeFiles.contains(rpath(distDir, f)))
            .foreach { file =>
      aos.putArchiveEntry(createEntry(file, archiveStem ++ "/" ++ rpath(distDir, file, "/"), posixMaskFunction))
      if (file.isDirectory) {
        aos.closeArchiveEntry()
        addFilesToArchive(file)
      } else {
        val in = new BufferedInputStream(new FileInputStream(file))
        try {
          IOUtils.copy(in, aos)
          aos.closeArchiveEntry()
        }
        finally {
          if(in != null)
            in.close()
        }
      }
    }
    addFilesToArchive(distDir)
    aos.close()
    targetDir / archiveName
  }

  def posixMaskFromFilesystem(file: File): Option[Int] = {
    val perms = Files.getPosixFilePermissions(file.toPath)
    val mask = perms.foldLeft(0) { (a, b) => a | (0x100 >> b.ordinal) }
    //println(s"Octal mask for ${file} is 0${mask.toOctalString}")
    Some(mask)
  }

  def posixMaskNone(_unused: File): Option[Int] =
    None

  def posixMaskFromPath(binDir: File)(file: File): Option[Int] =
    if (file.getAbsolutePath startsWith binDir.getAbsolutePath)
      Some(Integer.parseInt("0755", 8))
    else
      None

  def posixMaskFromBinPath(distDir: File): File => Option[Int] = {
    val binDir = distDir / "bin"
    posixMaskFromPath(binDir) _
  }

  private def createTarEntry(
    file: File,
    fileName: String,
    posixMask: File => Option[Int]) = {
    val archiveEntry = new TarArchiveEntry(file, fileName)
    posixMask(file) foreach { archiveEntry.setMode(_) }
    archiveEntry
  }

  private def createTarArchiveOutputStream(os: OutputStream) = {
    val tos = new TarArchiveOutputStream(os)
    tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
    tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
    tos
  }

  lazy val packArchiveSettings = Seq[Def.Setting[_]](
    packArchivePrefix := name.value,
    packArchiveName := s"${packArchivePrefix.value}-${version.value}",
    packArchiveExcludes := Seq.empty,
    packArchiveTgzArtifact := Artifact(packArchivePrefix.value, "arch", "tar.gz"),
    packArchiveTbzArtifact := Artifact(packArchivePrefix.value, "arch", "tar.bz2"),
    packArchiveTxzArtifact := Artifact(packArchivePrefix.value, "arch", "tar.xz"),
    packArchiveZipArtifact := Artifact(packArchivePrefix.value, "arch", "zip"),
    packArchivePosixMask := posixMaskFromBinPath(Pack.packTargetDir.value / Pack.packDir.value),

    packArchiveTgz := createArchive("tar.gz",
      (fos) => createTarArchiveOutputStream(new GzipCompressorOutputStream(fos)),
      createTarEntry).value,
    packArchiveTbz := createArchive("tar.bz2",
      (fos) => createTarArchiveOutputStream(new BZip2CompressorOutputStream(fos)),
      createTarEntry).value,
    packArchiveTxz := createArchive("tar.xz",
      (fos) => createTarArchiveOutputStream(new XZCompressorOutputStream(fos)),
      createTarEntry).value,
    packArchiveZip := createArchive("zip", new ZipArchiveOutputStream(_),
      (file, fileName, _) => new ZipArchiveEntry(file, fileName)).value,
    packArchive := Seq(
      packArchiveTgz.value,
      packArchiveZip.value))

  def publishPackArchiveTgz: SettingsDefinition = Seq(
    artifacts += packArchiveTgzArtifact.value,
    packagedArtifacts += packArchiveTgzArtifact.value -> packArchiveTgz.value)

  def publishPackArchiveTbz: SettingsDefinition = Seq(
    artifacts += packArchiveTbzArtifact.value,
    packagedArtifacts += packArchiveTbzArtifact.value -> packArchiveTbz.value)

  def publishPackArchiveTxz: SettingsDefinition = Seq(
    artifacts += packArchiveTxzArtifact.value,
    packagedArtifacts += packArchiveTxzArtifact.value -> packArchiveTxz.value)

  def publishPackArchiveZip: SettingsDefinition = Seq(
    artifacts += packArchiveZipArtifact.value,
    packagedArtifacts += packArchiveZipArtifact.value -> packArchiveZip.value)

  def publishPackArchives: SettingsDefinition =
    publishPackArchiveTgz ++ publishPackArchiveZip
}
