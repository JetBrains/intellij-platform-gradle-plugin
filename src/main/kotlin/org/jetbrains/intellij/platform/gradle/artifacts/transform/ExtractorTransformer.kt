// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

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
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.resolvers.ExtractorTransformerTargetNameResolver
import org.jetbrains.intellij.platform.gradle.utils.asPath
import javax.inject.Inject
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

/**
 * A transformer used for extracting files from archive artifacts.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class ExtractorTransformer @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val fileSystemOperations: FileSystemOperations,
) : TransformAction<ExtractorTransformer.Parameters> {

    interface Parameters : TransformParameters {

        @get:Internal
        val intellijPlatformDependency: ConfigurableFileCollection

        @get:Internal
        val jetbrainsRuntimeDependency: ConfigurableFileCollection
    }

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) = runLogging {
        val path = inputArtifact.asPath
        val targetName = ExtractorTransformerTargetNameResolver(path,)
            .runCatching { resolve() }
            .getOrNull()
            ?: return@runLogging

        val targetDirectory = outputs.dir(targetName)
        val extension = path.name.removePrefix(path.nameWithoutExtension.removeSuffix(".tar"))

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

            else -> throw IllegalArgumentException("Unknown type archive type '$extension' for '$path'")
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
            intellijPlatformDependency.from(intellijPlatformDependencyConfiguration)
            jetbrainsRuntimeDependency.from(jetbrainsRuntimeDependencyConfiguration)
        }
    }
}

//if (type == Rider && !OperatingSystem.current().isWindows) {
//    for (file in cacheDirectory.walkTopDown()) {
//        if (file.isFile
//            && (file.extension == "dylib"
//                    || file.extension == "py"
//                    || file.extension == "sh"
//                    || file.extension.startsWith("so")
//                    || file.name == "dotnet"
//                    || file.name == "env-wrapper"
//                    || file.name == "mono-sgen"
//                    || file.name == "BridgeService"
//                    || file.name == "JetBrains.Profiler.PdbServer"
//                    || file.name == "JBDeviceService"
//                    || file.name == "Rider.Backend")
//        ) {
//            setExecutable(cacheDirectory, file.relativeTo(cacheDirectory).toString(), context)
//        }
//    }
//}
