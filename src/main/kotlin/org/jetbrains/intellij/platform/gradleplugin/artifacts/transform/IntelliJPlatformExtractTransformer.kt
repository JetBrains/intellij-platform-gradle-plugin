// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.artifacts.transform

import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ZIP_TYPE
import org.gradle.api.attributes.Attribute
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

    private val IntelliJPlatformType.artifactPathPart
        get() = "$separator$groupId$separator$artifactId$separator"

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asPath

        val type = IntelliJPlatformType.values().find {
            input.pathString.contains(it.artifactPathPart) // com.jetbrains.intellij.idea/ideaIU
        } ?: return
        val version = input.getName(input.nameCount - 3)
        val extension = input.name.removePrefix(input.nameWithoutExtension.removeSuffix(".tar"))
        val targetDirectory = outputs.dir("$type-$version")

//        val cacheDirectory = getZipCacheDirectory(input)

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

//    private fun getZipCacheDirectory(zipFile: Path, project: Project, type: IntelliJPlatformType): Path {
//        if (ideaDependencyCachePath.isNotEmpty()) {
//            return File(ideaDependencyCachePath).apply {
//                mkdirs()
//            }
//        } else if (type == IntelliJPlatformType.Rider && OperatingSystem.current().isWindows) {
//            return project.buildDir.toPath()
//        }
//        return zipFile.parent
//    }
}

internal fun Project.applyIntellijPlatformExtractTransformer() {
    val extractedAttribute = Attribute.of("intellijPlatformExtracted", Boolean::class.javaObjectType)

    project.dependencies {
        attributesSchema {
            attribute(extractedAttribute)
        }

        artifactTypes.maybeCreate(ZIP_TYPE)
            .attributes
            .attribute(extractedAttribute, false)

        listOf(
            configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME),
            configurations.getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME),
        ).forEach {
            it.attributes {
                attributes.attribute(extractedAttribute, true)
            }
        }

        registerTransform(IntelliJPlatformExtractTransformer::class) {
            from.attribute(extractedAttribute, false)
            to.attribute(extractedAttribute, true)
        }
    }
}
