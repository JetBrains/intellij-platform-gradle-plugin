// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.utils

import com.jetbrains.plugin.structure.base.utils.*
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.jetbrains.intellij.platform.gradleplugin.debug
import java.nio.file.Path
import java.util.function.BiConsumer
import java.util.function.Predicate
import javax.inject.Inject

abstract class ArchiveUtils @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val fileSystemOperations: FileSystemOperations,
) {

    fun extract(
        archive: Path,
        targetDirectory: Path,
        context: String?,
        isUpToDate: Predicate<Path>? = null,
        markUpToDate: BiConsumer<Path, Path>? = null,
    ): Path {
        val name = archive.simpleName
        val markerFile = targetDirectory.resolve("markerFile")
        if (markerFile.exists() && (isUpToDate == null || isUpToDate.test(markerFile))) {
            return targetDirectory
        }

        targetDirectory.deleteQuietly()
        targetDirectory.createDir()

        debug(context, "Extracting: $name")

        when {
            name.endsWith(".zip") || name.endsWith(".sit") -> {
                fileSystemOperations.copy {
                    from(archiveOperations.zipTree(archive))
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

        markerFile.create()
        markUpToDate?.accept(targetDirectory, markerFile)
        return targetDirectory
    }
}
