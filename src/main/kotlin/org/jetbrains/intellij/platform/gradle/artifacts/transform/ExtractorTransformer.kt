// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.FileTree
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.resolvePlatformPath
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.*

/**
 * A transformer used for extracting files from archive artifacts.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class ExtractorTransformer @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val execOperations: ExecOperations,
    private val objectFactory: ObjectFactory,
    private val fileSystemOperations: FileSystemOperations,
) : TransformAction<TransformParameters.None> {

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    private val log = Logger(javaClass)

    private val tempDirectory = createTempDirectory()

    override fun transform(outputs: TransformOutputs) {
        runCatching {
            val path = inputArtifact.asPath
            val name = path.nameWithoutExtension.removeSuffix(".tar")
            val extension = path.name.removePrefix("$name.")
            val targetDirectory = outputs.dir(name).toPath()

            val artifactType = Attributes.ArtifactType.from(extension)
            val archiveOperator = when (artifactType) {
                Attributes.ArtifactType.ZIP,
                Attributes.ArtifactType.SIT,
                    -> archiveOperations::zipTree

                Attributes.ArtifactType.TAR_GZ,
                    -> archiveOperations::tarTree

                Attributes.ArtifactType.DMG,
                    -> ::dmgTree

                else
                    -> throw IllegalArgumentException("Unknown type archive type '$extension' for '$path'")
            }

            log.info("Extracting archive '$path' to directory '$targetDirectory'.")
            fileSystemOperations.copy {
                includeEmptyDirs = false
                from(archiveOperator(path))
                into(targetDirectory)
            }

            // Resolve the first directory that contains more than a single directory.
            // This approach helps eliminate `/Application Name.app/Contents/...` macOS directories or nested directory from the `tar.gz` archive.
            log.info("Resolving the content directory in '$targetDirectory'.")
            val platformPath = targetDirectory.resolvePlatformPath()

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

            when (artifactType) {
                Attributes.ArtifactType.DMG -> {
                    log.info("Unmounting DMG volume '$tempDirectory'.")
                    execOperations.exec {
                        commandLine("hdiutil", "detach", "-force", "-quiet", tempDirectory)
                    }
                }

                else -> {}
            }

            log.info("Extracting to '$targetDirectory' completed.")
        }.onFailure {
            log.error("${javaClass.canonicalName} execution failed.", it)
        }
    }

    private fun dmgTree(path: Path): FileTree {
        log.info("Extracting DMG archive '$path' to temporary directory.")

        val hdiutilInfo = ByteArrayOutputStream().use { os ->
            execOperations.exec {
                commandLine("hdiutil", "info")
                standardOutput = os
            }
            os.toString()
        }

        val resources = hdiutilInfo
            .split("================================================")
            .drop(1).associate {
                with(it.trim().lines()) {
                    first().split(" : ").last() to last().split("\t").last()
                }
            }

        resources[path.pathString]?.let { volume ->
            execOperations.exec {
                commandLine("hdiutil", "detach", "-force", "-quiet", volume)
            }
        }

        execOperations.exec {
            commandLine("hdiutil", "attach", "-readonly", "-noautoopen", "-noautofsck", "-noverify", "-nobrowse", "-mountpoint", "-quiet", tempDirectory, path.pathString)
        }

        return objectFactory.fileTree()
            .from(tempDirectory)
            .matching {
                exclude {
                    // DMG archives contain a symbolic link to the Applications directory and dot-directories located in the root of the archive,
                    // such as `.background` meta-directory we have to exclude.
                    it.file.run {
                        (name == "Applications" && Files.isSymbolicLink(toPath()))
                                || it.relativePath.startsWith('.')
                    }
                }
            }
    }

    companion object {
        internal fun register(dependencies: DependencyHandler) {
            dependencies.registerTransform(ExtractorTransformer::class) {
                from.attribute(Attributes.extracted, false)
                to.attribute(Attributes.extracted, true)
            }
        }
    }
}
