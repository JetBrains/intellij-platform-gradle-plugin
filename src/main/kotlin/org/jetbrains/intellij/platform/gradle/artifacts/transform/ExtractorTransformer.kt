// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RelativePath
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
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
        val coordinates: MapProperty<Attributes.AttributeType, List<String>>
    }

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    private val log = Logger(javaClass)

    override fun transform(outputs: TransformOutputs) {
        runCatching {
            val path = inputArtifact.asPath
            val pathSegments = path.toList().map { it.name }.windowed(3)
            val name = path.nameWithoutExtension.removeSuffix(".tar")
            val extension = path.name.removePrefix(name)

            val targetDirectory = outputs.dir(name)
            val attributeType = parameters.coordinates.get().entries
                .find { (_, value) -> value.any { pathSegments.contains(it.split(':')) } }
                ?.key ?: return

            targetDirectory.parentFile
                .resolve(Attributes.AttributeType::class.toString())
                .apply {
                    createNewFile()
                    writeText(attributeType.name)
                }

            val artifactType = Attributes.ArtifactType.valueOf(extension)
            val archiveOperator = when (artifactType) {
                Attributes.ArtifactType.ZIP,
                Attributes.ArtifactType.SIT,
                -> archiveOperations::zipTree

                Attributes.ArtifactType.TAR_GZ -> archiveOperations::tarTree
                Attributes.ArtifactType.DMG -> TODO("Not supported yet")
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

    companion object {
        internal fun register(
            dependencies: DependencyHandler,
            coordinates: Provider<Map<Attributes.AttributeType, DependencySet>>,
            compileClasspathConfiguration: Configuration,
            testCompileClasspathConfiguration: Configuration,
        ) {
            Attributes.ArtifactType.Archives.forEach {
                dependencies.artifactTypes.maybeCreate(it.name).attributes.attribute(Attributes.extracted, false)
            }

            listOf(compileClasspathConfiguration, testCompileClasspathConfiguration).forEach {
                it.attributes.attribute(Attributes.extracted, true)
            }

            dependencies.registerTransform(ExtractorTransformer::class) {
                from.attribute(Attributes.extracted, false)
                to.attribute(Attributes.extracted, true)

                parameters {
                    this.coordinates = coordinates.map {
                        it.mapValues { entry ->
                            entry.value.map { dependency -> dependency.group + ":" + dependency.name + ":" + dependency.version }
                        }
                    }
                }
            }
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
