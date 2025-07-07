// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.of
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes.ArtifactType
import org.jetbrains.intellij.platform.gradle.providers.DmgExtractorValueSource
import org.jetbrains.intellij.platform.gradle.resolvers.path.ProductInfoPathResolver
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.resolvePlatformPath
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.*

abstract class ExtractorService @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val providerFactory: ProviderFactory,
    private val fileSystemOperations: FileSystemOperations,
) : BuildService<BuildServiceParameters.None> {

    private val log = Logger(javaClass)

    fun extract(path: Path, targetDirectory: Path) {
        log.info("Extracting archive '$path' to directory '$targetDirectory'.")

        val name = path.nameWithoutExtension.removeSuffix(".tar")
        val extension = path.name.removePrefix("$name.")


        when (ArtifactType.from(extension)) {
            ArtifactType.ZIP, ArtifactType.SIT ->
                fileSystemOperations.copy {
                    includeEmptyDirs = false
                    from(archiveOperations.zipTree(path))
                    into(targetDirectory)
                }

            ArtifactType.TAR_GZ ->
                fileSystemOperations.copy {
                    includeEmptyDirs = false
                    from(archiveOperations.tarTree(path))
                    into(targetDirectory)
                }

            ArtifactType.DMG ->
                providerFactory.of(DmgExtractorValueSource::class) {
                    parameters.path = path.toFile()
                    parameters.target = targetDirectory.toFile()
                }.get()

            else
                -> throw IllegalArgumentException("Unknown type archive type '$extension' for '$path'")
        }

        // Resolve the first directory that contains more than a single directory.
        // This approach helps eliminate `/Application Name.app/Contents/...` macOS directories or nested directory from the `tar.gz` archive.
        log.info("Resolving the content directory in '$targetDirectory'.")
        val platformPath = targetDirectory.resolvePlatformPath()

        // Create .toolbox-ignore marker file next to product-info.json
        runCatching {
            val productInfo = ProductInfoPathResolver(platformPath).resolve()
            productInfo.parent.resolve(Constants.TOOLBOX_IGNORE).createFile()
        }

        log.info("The content directory is '$platformPath'.")

        // Move content from the resolved nested directory.
        if (platformPath != targetDirectory) {
            log.info("Copying the content from '$platformPath' to '$targetDirectory'.")
            platformPath.listDirectoryEntries().forEach { file ->
                val destination = targetDirectory.resolve(platformPath.relativize(file))
                destination.parent.createDirectories()
                file.moveTo(destination)
            }

            // Remove an empty directory.
            generateSequence(platformPath) { it.parent }
                .takeWhile { it != targetDirectory }
                .forEach { it.deleteExisting() }
        }

        log.info("Extracting to '$targetDirectory' completed.")
    }
}
