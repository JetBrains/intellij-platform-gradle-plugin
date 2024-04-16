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
import org.jetbrains.intellij.platform.gradle.resolvers.ExtractorTransformerTargetResolver
import org.jetbrains.intellij.platform.gradle.utils.Logger
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
        val intellijPlatformPluginDependency: ConfigurableFileCollection

        @get:Internal
        val jetbrainsRuntimeDependency: ConfigurableFileCollection
    }

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    private val log = Logger(javaClass)

    override fun transform(outputs: TransformOutputs) {
        runCatching {
            val path = inputArtifact.asPath
            val targetName = ExtractorTransformerTargetResolver(
                path,
                parameters.intellijPlatformDependency,
                parameters.intellijPlatformPluginDependency,
                parameters.jetbrainsRuntimeDependency,
            ).resolve()

            val extension = path.name.removePrefix(path.nameWithoutExtension.removeSuffix(".tar"))
            val targetDirectory = outputs.dir(targetName)

            val archiveOperator = when (extension) {
                ".zip", ".sit" -> archiveOperations::zipTree
                ".tar.gz" -> archiveOperations::tarTree
                else -> throw IllegalArgumentException("Unknown type archive type '$extension' for '$path'")
            }

            fileSystemOperations.copy {
                from(archiveOperator(path))
                into(targetDirectory)
                eachFile {
                    val segments = with(relativePath.segments) {
                        when {
                            size > 2 && get(0).endsWith(".app") && get(1) == "Contents" -> drop(2).toTypedArray()
                            else -> this
                        }
                    }
                    relativePath = RelativePath(true, *segments)
                }
            }
        }.onFailure {
            log.error("${javaClass.canonicalName} execution failed.", it)
        }
    }
}

internal fun DependencyHandler.applyExtractorTransformer(
    compileClasspathConfiguration: Configuration,
    testCompileClasspathConfiguration: Configuration,
    intellijPlatformDependencyConfiguration: Configuration,
    intellijPlatformPluginDependencyConfiguration: Configuration,
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
            intellijPlatformPluginDependency.from(intellijPlatformPluginDependencyConfiguration)
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
