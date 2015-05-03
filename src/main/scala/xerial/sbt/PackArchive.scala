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

trait PackArchive {
  val packArchivePrefix = SettingKey[String]("prefix of (prefix)-(version).(format) archive file name")
  val packArchiveTgzArtifact = SettingKey[Artifact]("tar.gz archive artifact")
  val packArchiveTbzArtifact = SettingKey[Artifact]("tar.bz2 archive artifact")
  val packArchiveTxzArtifact = SettingKey[Artifact]("tar.xz archive artifact")
  val packArchiveZipArtifact = SettingKey[Artifact](" zip archive artifact")
  val packArchiveTgz = TaskKey[File]("pack-tgz-archive", "create a tar.gz archive of the distributable package")
  val packArchiveTbz = TaskKey[File]("pack-tbz-archive", "create a tar.bz2 archive of the distributable package")
  val packArchiveTxz = TaskKey[File]("pack-txz-archive", "create a tar.xz archive of the distributable package")
  val packArchiveZip = TaskKey[File]("pack-zip-archive", "create a zip archive of the distributable package")
  val packArchive = TaskKey[Seq[File]]("pack-archive", "create a tar.gz, tar.bz2, tar.xz and a zip archive of the distributable package")

  private def createArchive(
    archiveSuffix: String,
    createOutputStream: (OutputStream) => ArchiveOutputStream,
    createEntry: (File, String, File) => ArchiveEntry) = Def.task {
    val out = streams.value
    val targetDir: File = target.value
    val distDir: File = Pack.pack.value
    val binDir = distDir / "bin"
    val archiveStem = s"${packArchivePrefix.value}-${version.value}"
    val archiveName = s"${archiveStem}.${archiveSuffix}"
    out.log.info("Generating " + rpath(baseDirectory.value, targetDir / archiveName))
    val aos = createOutputStream(new BufferedOutputStream(new FileOutputStream(targetDir / archiveName)))
    val excludeFiles = Set("Makefile", "VERSION")
    def addFilesToArchive(dir: File): Unit = dir.listFiles.
      filterNot(f => excludeFiles.contains(rpath(distDir, f))).foreach { file =>
        aos.putArchiveEntry(createEntry(file, archiveStem ++ "/" ++ rpath(distDir, file, "/"), binDir))
        if (file.isDirectory) {
          aos.closeArchiveEntry()
          addFilesToArchive(file)
        } else {
          IOUtils.copy(new BufferedInputStream(new FileInputStream(file)), aos)
          aos.closeArchiveEntry()
        }
      }
    addFilesToArchive(distDir)
    aos.close()
    target.value / archiveName
  }

  private def createTarEntry(file: File, fileName: String, binDir: File) = {
    val archiveEntry = new TarArchiveEntry(file, fileName)
    if (file.getAbsolutePath startsWith binDir.getAbsolutePath)
      archiveEntry.setMode(Integer.parseInt("0755", 8))
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
    packArchiveTgzArtifact := Artifact(packArchivePrefix.value, "arch", "tar.gz"),
    packArchiveTbzArtifact := Artifact(packArchivePrefix.value, "arch", "tar.bz2"),
    packArchiveTxzArtifact := Artifact(packArchivePrefix.value, "arch", "tar.xz"),
    packArchiveZipArtifact := Artifact(packArchivePrefix.value, "arch", "zip"),
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

  def publishPackTgzArchive: SettingsDefinition = Seq(
    artifacts += packArchiveTgzArtifact.value,
    packagedArtifacts += packArchiveTgzArtifact.value -> packArchiveTgz.value)

  def publishPackTbzArchive: SettingsDefinition = Seq(
    artifacts += packArchiveTbzArtifact.value,
    packagedArtifacts += packArchiveTbzArtifact.value -> packArchiveTbz.value)

  def publishPackTxzArchive: SettingsDefinition = Seq(
    artifacts += packArchiveTxzArtifact.value,
    packagedArtifacts += packArchiveTxzArtifact.value -> packArchiveTxz.value)

  def publishPackZipArchive: SettingsDefinition = Seq(
    artifacts += packArchiveZipArtifact.value,
    packagedArtifacts += packArchiveZipArtifact.value -> packArchiveZip.value)

  def publishPackArchives: SettingsDefinition =
    publishPackTgzArchive ++ publishPackZipArchive
}
