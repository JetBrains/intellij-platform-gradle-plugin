// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.artifacts.transform

import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ZIP_TYPE
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.plugins.JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradleplugin.asPath
import java.io.File.separator
import javax.inject.Inject
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

// TODO: Allow for providing custom IDE dir?
@DisableCachingByDefault(because = "Not worth caching")
abstract class IntelliJPlatformExtractTransformer @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val fileSystemOperations: FileSystemOperations,
) : TransformAction<TransformParameters.None> {

    @get:Classpath
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asPath
        val extension = input.name.removePrefix(input.nameWithoutExtension.removeSuffix(".tar"))
        val (groupId, artifactId, version) = input.pathString.split(separator).dropLast(2).takeLast(3)

        val targetDirectory = listOf(
            { // check if input is an IntelliJ Platform SDK artifact
                IntelliJPlatformType.values()
                    .find { groupId == it.groupId && artifactId == it.artifactId }
                    ?.let { "$it-$version" }
            },
            { // check if input is an IntelliJ Platform Plugin fetched from JetBrains Marketplace
                val marketplaceGroup = "com.jetbrains.plugins"
                val channel = when {
                    groupId == marketplaceGroup -> ""
                    groupId.endsWith(".$marketplaceGroup") -> groupId.dropLast(marketplaceGroup.length + 1)
                    else -> return@listOf null
                }
                "$artifactId-$version" + "@$channel".takeIf { channel.isNotEmpty() }.orEmpty()
            },
        ).firstNotNullOfOrNull { it() }?.let { outputs.dir(it) } ?: return

        when (extension) {
            ".zip", ".sit" -> {
                fileSystemOperations.copy {
                    from(archiveOperations.zipTree(input))
                    into(targetDirectory)
                }
            }

            ".tar.gz" -> {
                fileSystemOperations.copy {
                    from(archiveOperations.tarTree(input))
                    into(targetDirectory)
                }
            }

            else -> throw IllegalArgumentException("Unknown type archive type: $extension")
        }
    }
}

internal fun Project.applyIntellijPlatformExtractTransformer() {
    project.dependencies {
        attributesSchema {
            attribute(Configurations.Attributes.extracted)
        }

        artifactTypes.maybeCreate(ZIP_TYPE)
            .attributes
            .attribute(Configurations.Attributes.extracted, false)

        listOf(
            configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME),
            configurations.getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME),
        ).forEach {
            it.attributes {
                attributes.attribute(Configurations.Attributes.extracted, true)
            }
        }

        registerTransform(IntelliJPlatformExtractTransformer::class) {
            from
                .attribute(Configurations.Attributes.extracted, false)
            to
                .attribute(Configurations.Attributes.extracted, true)
        }
    }
}
