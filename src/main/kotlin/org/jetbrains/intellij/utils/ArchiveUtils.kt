package org.jetbrains.intellij.utils

import org.gradle.api.Incubating
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecOperations
import org.jetbrains.intellij.debug
import java.io.File
import java.util.function.BiConsumer
import java.util.function.Predicate
import javax.inject.Inject

open class ArchiveUtils @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
) {

    @Suppress("UnstableApiUsage")
    @Incubating
    fun extract(
        archiveFile: File,
        targetDirectory: File,
        context: String?,
        isUpToDate: Predicate<File>? = null,
        markUpToDate: BiConsumer<File, File>? = null,
    ): File {
        val name = archiveFile.name
        val markerFile = File(targetDirectory, "markerFile")
        if (markerFile.exists() && (isUpToDate == null || isUpToDate.test(markerFile))) {
            return targetDirectory
        }

        targetDirectory.deleteRecursively()
        targetDirectory.mkdirs()

        debug(context, "Extracting: $name")

        if (name.endsWith(".tar.gz") && OperatingSystem.current().isWindows) {
            execOperations.exec {
                it.commandLine("tar", "-xpf", archiveFile.absolutePath, "--directory", targetDirectory.absolutePath)
            }
        } else {
            val decompressor = when {
                name.endsWith(".zip") || name.endsWith(".sit") -> archiveOperations::zipTree
                name.endsWith(".tar.gz") -> archiveOperations::tarTree
                else -> throw IllegalArgumentException("Unknown type archive type: $name")
            }
            fileSystemOperations.copy {
                it.from(decompressor.invoke(archiveFile))
                it.into(targetDirectory)
            }
        }

        debug(context, "Extracted: $name")

        markerFile.createNewFile()
        markUpToDate?.accept(targetDirectory, markerFile)
        return targetDirectory
    }
}
