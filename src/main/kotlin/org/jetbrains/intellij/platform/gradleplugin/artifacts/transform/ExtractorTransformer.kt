// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.artifacts.transform

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ZIP_TYPE
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradleplugin.asPath
import java.io.File
import java.io.File.separator
import javax.inject.Inject
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

// TODO: Allow for providing custom IDE dir?
@DisableCachingByDefault(because = "Not worth caching")
abstract class ExtractorTransformer @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val fileSystemOperations: FileSystemOperations,
) : TransformAction<ExtractorTransformer.Parameters> {

    interface Parameters : TransformParameters {

        @get:Internal
        val intellijPlatform: ConfigurableFileCollection

        @get:Internal
        val jetbrainsRuntime: ConfigurableFileCollection
    }

    @get:Classpath
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val (file, path) = with(inputArtifact.get()) { asFile to asPath }
        val extension = path.name.removePrefix(path.nameWithoutExtension.removeSuffix(".tar"))
        val (groupId, artifactId, version) = path.pathString.split(separator).dropLast(2).takeLast(3)
        // TODO: if a local ZIP file, i.e. with local plugin will be passed to PLUGIN configuration — that most likely will fail

        val targetDirectory = when (file) {
            in parameters.intellijPlatform -> {
                IntelliJPlatformType.values().find { groupId == it.groupId && artifactId == it.artifactId }?.let { "$it-$version" }
            }

            in parameters.jetbrainsRuntime -> version

            in emptyList<File>() -> {
                val marketplaceGroup = "com.jetbrains.plugins"
                val channel = when {
                    groupId == marketplaceGroup -> ""
                    groupId.endsWith(".$marketplaceGroup") -> groupId.dropLast(marketplaceGroup.length + 1)
                    else -> null
                } ?: return
                "$artifactId-$version" + "@$channel".takeIf { channel.isNotEmpty() }.orEmpty()
            }

            else -> null
        }?.let { outputs.dir(it) } ?: return

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
                }
            }

            else -> throw IllegalArgumentException("Unknown type archive type: $extension")
        }
    }
}

internal fun DependencyHandler.applyExtractorTransformer(
    compileClasspathConfiguration: Configuration,
    testCompileClasspathConfiguration: Configuration,
    intellijPlatformDependencyConfiguration: Configuration,
    jetbrainsRuntimeDependencyConfiguration: Configuration,
) {
    artifactTypes.maybeCreate(ZIP_TYPE)
        .attributes
        .attribute(Attributes.extracted, false)

    artifactTypes.maybeCreate("tar.gz")
        .attributes
        .attribute(Attributes.extracted, false)

    compileClasspathConfiguration
        .attributes
        .attribute(Attributes.extracted, true)

    testCompileClasspathConfiguration
        .attributes
        .attribute(Attributes.extracted, true)

    registerTransform(ExtractorTransformer::class) {
        from
            .attribute(Attributes.extracted, false)
        to
            .attribute(Attributes.extracted, true)

        parameters {
            intellijPlatform.from(intellijPlatformDependencyConfiguration)
            jetbrainsRuntime.from(jetbrainsRuntimeDependencyConfiguration)
        }
    }
}
