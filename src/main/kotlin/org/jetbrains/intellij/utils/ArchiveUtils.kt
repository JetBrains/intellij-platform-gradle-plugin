// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.utils

import com.jetbrains.plugin.structure.base.utils.simpleName
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.jetbrains.intellij.debug
import java.io.File
import java.util.function.BiConsumer
import java.util.function.Predicate
import javax.inject.Inject

internal abstract class ArchiveUtils @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val fileSystemOperations: FileSystemOperations,
) {

    fun extract(
        archiveFile: File,
        targetDirectory: File,
        context: String?,
        isUpToDate: Predicate<File>? = null,
        markUpToDate: BiConsumer<File, File>? = null,
    ): File {
        val archive = archiveFile.toPath() // TODO: migrate archiveFile to Path
        val name = archive.simpleName
        val markerFile = File(targetDirectory, "markerFile")
        if (markerFile.exists() && (isUpToDate == null || isUpToDate.test(markerFile))) {
            return targetDirectory
        }

        targetDirectory.deleteRecursively()
        targetDirectory.mkdirs()

        debug(context, "Extracting: $name")

        when {
            name.endsWith(".zip") || name.endsWith(".sit") -> {
                fileSystemOperations.copy {
                    from(archiveOperations.zipTree(archiveFile))
                    into(targetDirectory)
                }
            }

            name.endsWith(".tar.gz") -> {
                fileSystemOperations.copy {
                    from(archiveOperations.tarTree(archive))
                    into(targetDirectory)
                }
            }

            else -> throw IllegalArgumentException("Unknown type archive type: $name")
        }

        debug(context, "Extracted: $name")

        markerFile.createNewFile()
        markUpToDate?.accept(targetDirectory, markerFile)
        return targetDirectory
    }
}
