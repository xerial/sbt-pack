package xerial.sbt.pack

import java.io._

import org.apache.commons.compress.archivers._
import org.apache.commons.compress.archivers.tar._
import org.apache.commons.compress.archivers.zip._
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.utils.IOUtils
import sbt.Keys._
import sbt._

trait PackArchive {

  import PackPlugin.autoImport._

  private def createArchive(
      archiveSuffix: String,
      createOutputStream: (OutputStream) => ArchiveOutputStream,
      createEntry: (File, String, File) => ArchiveEntry
  ) = Def.task {
    val out             = streams.value
    val targetDir: File = packTargetDir.value
    val distDir: File   = pack.value // run pack command here
    val binDir          = distDir / "bin"
    val archiveStem     = s"${packArchiveStem.value}"
    val archiveName     = s"${packArchiveName.value}.${archiveSuffix}"
    out.log.info("Generating " + rpath(baseDirectory.value, targetDir / archiveName))
    val aos          = createOutputStream(new BufferedOutputStream(new FileOutputStream(targetDir / archiveName)))
    val excludeFiles = packArchiveExcludes.value.toSet
    def addFilesToArchive(dir: File): Unit =
      Option(dir.listFiles)
        .getOrElse(Array.empty)
        .filterNot(f => excludeFiles.contains(rpath(distDir, f)))
        .foreach { file =>
          aos.putArchiveEntry(createEntry(file, archiveStem ++ "/" ++ rpath(distDir, file, "/"), binDir))
          if (file.isDirectory) {
            aos.closeArchiveEntry()
            addFilesToArchive(file)
          } else {
            val in = new BufferedInputStream(new FileInputStream(file))
            try {
              IOUtils.copy(in, aos)
              aos.closeArchiveEntry()
            } finally {
              if (in != null)
                in.close()
            }
          }
        }
    addFilesToArchive(distDir)
    aos.close()
    targetDir / archiveName
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

  private def createZipEntry(file: File, fileName: String, binDir: File) = {
    val archiveEntry = new ZipArchiveEntry(file, fileName)
    if (file.getAbsolutePath.startsWith(binDir.getAbsolutePath)) {
      archiveEntry.setUnixMode(Integer.parseInt("0755", 8))
    }
    archiveEntry
  }

  lazy val packArchiveSettings = Seq[Def.Setting[_]](
    packArchivePrefix := name.value,
    packArchiveName := s"${packArchivePrefix.value}-${version.value}",
    packArchiveStem := s"${packArchiveName.value}",
    packArchiveExcludes := Seq.empty,
    packArchiveTgzArtifact := Artifact(packArchivePrefix.value, "arch", "tar.gz"),
    packArchiveTbzArtifact := Artifact(packArchivePrefix.value, "arch", "tar.bz2"),
    packArchiveTxzArtifact := Artifact(packArchivePrefix.value, "arch", "tar.xz"),
    packArchiveZipArtifact := Artifact(packArchivePrefix.value, "arch", "zip"),
    packArchiveTgz := createArchive(
      "tar.gz",
      (fos) => createTarArchiveOutputStream(new GzipCompressorOutputStream(fos)),
      createTarEntry
    ).value,
    packArchiveTbz := createArchive(
      "tar.bz2",
      (fos) => createTarArchiveOutputStream(new BZip2CompressorOutputStream(fos)),
      createTarEntry
    ).value,
    packArchiveTxz := createArchive(
      "tar.xz",
      (fos) => createTarArchiveOutputStream(new XZCompressorOutputStream(fos)),
      createTarEntry
    ).value,
    packArchiveZip := createArchive("zip", new ZipArchiveOutputStream(_), createZipEntry).value,
    packArchive := Seq(packArchiveTgz.value, packArchiveZip.value)
  )

  def publishPackArchiveTgz: SettingsDefinition = Seq(
    artifacts += packArchiveTgzArtifact.value,
    packagedArtifacts += packArchiveTgzArtifact.value -> packArchiveTgz.value
  )

  def publishPackArchiveTbz: SettingsDefinition = Seq(
    artifacts += packArchiveTbzArtifact.value,
    packagedArtifacts += packArchiveTbzArtifact.value -> packArchiveTbz.value
  )

  def publishPackArchiveTxz: SettingsDefinition = Seq(
    artifacts += packArchiveTxzArtifact.value,
    packagedArtifacts += packArchiveTxzArtifact.value -> packArchiveTxz.value
  )

  def publishPackArchiveZip: SettingsDefinition = Seq(
    artifacts += packArchiveZipArtifact.value,
    packagedArtifacts += packArchiveZipArtifact.value -> packArchiveZip.value
  )

  def publishPackArchives: SettingsDefinition =
    publishPackArchiveTgz ++ publishPackArchiveZip
}
