// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ZIP_TYPE
import org.gradle.api.file.*
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.io.File.separator
import javax.inject.Inject
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

/**
 * A transformer used for extracting IDEs used by the IntelliJ Plugin Verifier.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class PluginVerifierIdeExtractorTransformer @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val fileSystemOperations: FileSystemOperations,
) : TransformAction<PluginVerifierIdeExtractorTransformer.Parameters> {

    interface Parameters : TransformParameters {

        @get:Internal
        val downloadDirectory: DirectoryProperty

        @get:Internal
        val binaryReleaseDependencies: ConfigurableFileCollection
    }

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val path = inputArtifact.asPath
        val extension = path.name.removePrefix(path.nameWithoutExtension.removeSuffix(".tar"))
        val (groupId, artifactId, version) = path.absolutePathString().split(separator).dropLast(2).takeLast(3)
        // TODO: if a local ZIP file, i.e. with local plugin will be passed to PLUGIN configuration — that most likely will fail

        val type = IntelliJPlatformType.values().find {
            it.binary?.let { it.groupId == groupId && it.artifactId == artifactId } == true
        } ?: return

        val targetDirectory = parameters.downloadDirectory.dir("$type-$version").asPath
        outputs.file("path.txt").writeText(targetDirectory.absolutePathString())

        when (extension) {
            ".zip", ".sit" -> {
                fileSystemOperations.copy {
                    from(archiveOperations.zipTree(path))
                    into(targetDirectory)
                }
            }

            ".tar.gz" -> {
                fileSystemOperations.copy {
                    from(archiveOperations.tarTree(path))
                    into(targetDirectory)
                    includeEmptyDirs = false
                    eachFile {
                        this.path = this.path.split('/', limit = 2).last()
                    }
                }
            }

            else -> throw IllegalArgumentException("Unknown type archive type '$extension' for '$path'")
        }
    }
}

internal fun DependencyHandler.applyPluginVerifierIdeExtractorTransformer(
    intellijPluginVerifierIdesDependencyConfiguration: Configuration,
    downloadDirectoryProvider: Provider<Directory>,
) {
    artifactTypes.maybeCreate(ZIP_TYPE)
        .attributes
        .attribute(Attributes.binaryReleaseExtracted, false)

    artifactTypes.maybeCreate("tar.gz")
        .attributes
        .attribute(Attributes.binaryReleaseExtracted, false)

    registerTransform(PluginVerifierIdeExtractorTransformer::class) {
        from
            .attribute(Attributes.binaryReleaseExtracted, false)

        to
            .attribute(Attributes.binaryReleaseExtracted, true)

        parameters {
            binaryReleaseDependencies.from(intellijPluginVerifierIdesDependencyConfiguration)
            downloadDirectory.convention(downloadDirectoryProvider)
        }
    }
}
