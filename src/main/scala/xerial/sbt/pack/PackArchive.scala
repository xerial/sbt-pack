package xerial.sbt.pack

import java.io.*
import org.apache.commons.compress.archivers.*
import org.apache.commons.compress.archivers.tar.*
import org.apache.commons.compress.archivers.zip.*
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.io.IOUtils
import sbt.Keys.*
import sbt.*
import PluginCompat.*
import PluginCompat.toFile

trait PackArchive {

  import PackPlugin.autoImport._

  private def createArchive[EntryType <: ArchiveEntry](
      archiveSuffix: String,
      createOutputStream: (OutputStream) => ArchiveOutputStream[EntryType],
      createEntry: (File, String, File) => EntryType
  ) = Def.task {
    val out                    = streams.value
    val targetDir: File        = packTargetDir.value
    val distDir: File          = pack.value // run pack command here
    val binDir                 = distDir / "bin"
    val archiveStem            = s"${packArchiveStem.value}"
    val archiveBaseDir: String = if (archiveStem.isEmpty) "" else s"${archiveStem}/"
    val archiveName            = s"${packArchiveName.value}.${archiveSuffix}"
    out.log.info("Generating " + rpath(baseDirectory.value, targetDir / archiveName))
    val aos          = createOutputStream(new BufferedOutputStream(new FileOutputStream(targetDir / archiveName)))
    val excludeFiles = packArchiveExcludes.value.toSet
    def addFilesToArchive(dir: File): Unit =
      Option(dir.listFiles)
        .getOrElse(Array.empty[File])
        .filterNot(f => excludeFiles.contains(rpath(distDir, f)))
        .foreach { file =>
          aos.putArchiveEntry(createEntry(file, archiveBaseDir ++ rpath(distDir, file, "/"), binDir))
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

  private def createTarEntry(file: File, fileName: String, binDir: File): TarArchiveEntry = {
    val archiveEntry = new TarArchiveEntry(file, fileName)
    if (file.getAbsolutePath startsWith binDir.getAbsolutePath) {
      archiveEntry.setMode(Integer.parseInt("0755", 8))
    }
    archiveEntry
  }

  private def createTarArchiveOutputStream(os: OutputStream): TarArchiveOutputStream = {
    val tos = new TarArchiveOutputStream(os)
    tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
    tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
    tos
  }

  private def createZipEntry(file: File, fileName: String, binDir: File): ZipArchiveEntry = {
    val archiveEntry = new ZipArchiveEntry(file, fileName)
    if (file.getAbsolutePath.startsWith(binDir.getAbsolutePath)) {
      archiveEntry.setUnixMode(Integer.parseInt("0755", 8))
    }
    archiveEntry
  }

  lazy val packArchiveSettings = Seq[Def.Setting[_]](
    packArchivePrefix      := name.value,
    packArchiveName        := s"${packArchivePrefix.value}-${version.value}",
    packArchiveStem        := s"${packArchiveName.value}",
    packArchiveExcludes    := Seq.empty,
    packArchiveTgzArtifact := Artifact(packArchivePrefix.value, "arch", "tar.gz"),
    packArchiveTbzArtifact := Artifact(packArchivePrefix.value, "arch", "tar.bz2"),
    packArchiveTxzArtifact := Artifact(packArchivePrefix.value, "arch", "tar.xz"),
    packArchiveZipArtifact := Artifact(packArchivePrefix.value, "arch", "zip"),
    Def.derive(
      packArchiveTgz := createArchive[TarArchiveEntry](
        "tar.gz",
        (fos) => createTarArchiveOutputStream(new GzipCompressorOutputStream(fos)),
        createTarEntry
      ).value
    ),
    Def.derive(
      packArchiveTbz := createArchive[TarArchiveEntry](
        "tar.bz2",
        (fos) => createTarArchiveOutputStream(new BZip2CompressorOutputStream(fos)),
        createTarEntry
      ).value
    ),
    Def.derive(
      packArchiveTxz := createArchive[TarArchiveEntry](
        "tar.xz",
        (fos) => createTarArchiveOutputStream(new XZCompressorOutputStream(fos)),
        createTarEntry
      ).value
    ),
    Def.derive(
      packArchiveZip := createArchive[ZipArchiveEntry]("zip", new ZipArchiveOutputStream(_), createZipEntry).value
    ),
    Def.derive(packArchive := Seq(packArchiveTgz.value, packArchiveZip.value))
  )

  def publishPackArchiveTgz: SettingsDefinition =
    addArtifact(Def.setting(packArchiveTgzArtifact.value), Runtime / packArchiveTgz)

  def publishPackArchiveTbz: SettingsDefinition =
    addArtifact(Def.setting(packArchiveTbzArtifact.value), Runtime / packArchiveTbz)

  def publishPackArchiveTxz: SettingsDefinition =
    addArtifact(Def.setting(packArchiveTxzArtifact.value), Runtime / packArchiveTxz)

  def publishPackArchiveZip: SettingsDefinition =
    addArtifact(Def.setting(packArchiveZipArtifact.value), Runtime / packArchiveZip)

  def publishPackArchives: SettingsDefinition =
    publishPackArchiveTgz ++ publishPackArchiveZip
}
