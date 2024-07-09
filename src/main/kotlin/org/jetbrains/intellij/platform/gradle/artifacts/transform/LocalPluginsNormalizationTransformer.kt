// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import javax.inject.Inject
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * A transformer that adjusts the local plugin path into a nested layout if it directly contains the plugin content.
 * This layout prevents the plugin from loading it inside the IDE.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class LocalPluginsNormalizationTransformers @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
) : TransformAction<TransformParameters.None> {

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    private val log = Logger(javaClass)

    override fun transform(outputs: TransformOutputs) {
        runCatching {
            val path = inputArtifact.asPath

            when {
                path.resolve("lib").exists() -> {
                    val targetDirectory = outputs.dir(path.name).resolve(path.name)

                    log.info(
                        """
                        The local plugin path is set to the directory that directly contains the plugin content.
                        This layout prevents the plugin from loading it inside the IDE.
                        To resolve this, the plugin content has been moved to a nested directory: '$targetDirectory'
                        """.trimIndent()
                    )

                    fileSystemOperations.copy {
                        includeEmptyDirs = false
                        from(path)
                        into(targetDirectory)
                    }
                }

                else -> outputs.dir(path)
            }
        }.onFailure {
            log.error("${javaClass.canonicalName} execution failed.", it)
        }
    }

    companion object {
        internal fun register(dependencies: DependencyHandler) {
            Attributes.ArtifactType.values().forEach {
                dependencies.artifactTypes.maybeCreate(it.toString())
                    .attributes.attribute(Attributes.localPluginsNormalized, false)
            }

            dependencies.registerTransform(LocalPluginsNormalizationTransformers::class) {
                from.attribute(Attributes.localPluginsNormalized, false)
                to.attribute(Attributes.localPluginsNormalized, true)
            }
        }
    }
}
