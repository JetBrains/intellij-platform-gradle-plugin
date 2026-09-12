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
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import kotlin.concurrent.withLock
import kotlin.io.path.*

private const val EXTRACTION_COMPLETE_MARKER = ".intellij-platform-extracted"

internal fun extractionCompleteMarker(targetDirectory: Path): Path =
    targetDirectory.resolveSibling(".${targetDirectory.name}$EXTRACTION_COMPLETE_MARKER")

abstract class ExtractorService @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val providerFactory: ProviderFactory,
    private val fileSystemOperations: FileSystemOperations,
) : BuildService<BuildServiceParameters.None> {

    private companion object {
        val EXTRACTION_LOCK = ReentrantLock()
    }

    private val log = Logger(javaClass)

    /**
     * Extracts [path] into [targetDirectory], or reuses the target when its completion marker is present.
     *
     * @param path archive to extract.
     * @param targetDirectory directory in which to publish the extracted content.
     */
    fun extract(path: Path, targetDirectory: Path) {
        extract(targetDirectory) { path }
    }

    /**
     * The lazy path variant lets cache-backed callers avoid resolving or downloading the archive when a completed
     * extraction is already available. The completion check intentionally stays inside this BuildService so it is not
     * captured as a configuration-cache input.
     */
    @OptIn(ExperimentalPathApi::class)
    internal fun extract(targetDirectory: Path, path: () -> Path) {
        val target = targetDirectory.toAbsolutePath().normalize()
        val parent = requireNotNull(target.parent) { "Extraction target '$target' has no parent directory." }

        // FileChannel locks overlap within one JVM, so follow the local Ivy writer and serialize before acquiring one.
        EXTRACTION_LOCK.withLock {
            parent.createDirectories()
            val completionMarker = extractionCompleteMarker(target)
            val lockFile = target.resolveSibling(".${target.name}.lock")

            FileChannel.open(lockFile, CREATE, WRITE).use { channel ->
                channel.lock().use {
                    if (target.isDirectory() && completionMarker.isRegularFile()) {
                        log.info("Reusing the completed archive extraction in '$target'.")
                        return@use
                    }

                    completionMarker.deleteIfExists()
                    if (target.exists()) {
                        target.deleteRecursively()
                    }

                    try {
                        target.createDirectories()
                        extractArchive(path(), target)
                        completionMarker.writeText("complete\n")
                    } catch (throwable: Throwable) {
                        runCatching { target.deleteRecursively() }
                        throw throwable
                    }
                }
            }
        }
    }

    private fun extractArchive(path: Path, targetDirectory: Path) {
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
                val destination = targetDirectory.resolve(file.name)
                destination.parent.createDirectories()
                file.moveTo(destination, REPLACE_EXISTING)
            }

            // Remove an empty directory.
            generateSequence(platformPath) { it.parent }
                .takeWhile { it != targetDirectory }
                .forEach { it.deleteExisting() }
        }

        log.info("Extracting to '$targetDirectory' completed.")
    }
}
