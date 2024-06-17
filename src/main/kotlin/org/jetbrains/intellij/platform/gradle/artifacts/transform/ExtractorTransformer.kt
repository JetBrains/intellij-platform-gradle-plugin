// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.gradle.api.artifacts.Configuration
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

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
            val targetDirectory = outputs.dir(name)

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
            val root = generateSequence(targetDirectory) { parent ->
                val entry = parent.listFiles()
                    ?.singleOrNull() // pick an entry if it is a singleton in a directory
                    ?.takeIf { it.isDirectory } // and this entry is a directory
                    ?: return@generateSequence null

                fun File.getOrNull(name: String) = listFiles { file -> file.name == name }?.singleOrNull()

                when {
                    entry.name.endsWith(".app") -> entry.getOrNull("Contents") // eliminate `/Application Name.app/Contents/...`
                    entry.getOrNull("product-info.json") != null -> entry// set the root to the directory containing `product-info.json`
                    entry.getOrNull("Resources")?.getOrNull("product-info.json") != null -> entry// set the root to the directory containing `Resources/product-info.json`
                    entry.getOrNull("lib") != null -> null // stop when `lib/` is inside, even if it's a singleton
                    else -> null
                }
            }.last()
            log.info("The content directory is '$root'.")

            // Move content from the resolved nested directory.
            if (root != targetDirectory) {
                log.info("Copying the content from '$root' to '$targetDirectory'.")
                root.listFiles()?.forEach { file ->
                    val destination = targetDirectory.resolve(file.toRelativeString(root))
                    destination.parentFile?.mkdirs()
                    file.renameTo(destination)
                }

                // Remove empty directory.
                generateSequence(root) { it.parentFile }
                    .takeWhile { it != targetDirectory }
                    .forEach { it.delete() }
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
                    // DMG archives contain a symbolic link to Applications directory and dot-directories located in the root of the archive,
                    // such as `.background` meta-directory, we have to exclude.
                    it.file.run {
                        (name == "Applications" && Files.isSymbolicLink(toPath()))
                                || it.relativePath.startsWith('.')
                    }
                }
            }
    }

    companion object {
        internal fun register(
            dependencies: DependencyHandler,
            compileClasspathConfiguration: Configuration,
            testCompileClasspathConfiguration: Configuration,
        ) {
            Attributes.ArtifactType.Archives.forEach {
                dependencies.artifactTypes.maybeCreate(it.toString())
                    .attributes.attribute(Attributes.extracted, false)
            }

            listOf(compileClasspathConfiguration, testCompileClasspathConfiguration).forEach {
                it.attributes.attribute(Attributes.extracted, true)
            }

            dependencies.registerTransform(ExtractorTransformer::class) {
                from.attribute(Attributes.extracted, false)
                to.attribute(Attributes.extracted, true)
            }
        }
    }
}
