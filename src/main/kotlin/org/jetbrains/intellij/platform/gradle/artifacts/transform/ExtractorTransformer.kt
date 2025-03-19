// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.services.ExtractorService
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import javax.inject.Inject
import kotlin.io.path.nameWithoutExtension

/**
 * A transformer used for extracting files from archive artifacts.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class ExtractorTransformer @Inject constructor(
) : TransformAction<ExtractorTransformer.Parameters> {

    interface Parameters : TransformParameters {

        /**
         * The archive extractor service.
         */
        @get:Internal
        val extractorService: Property<ExtractorService>
    }

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    private val log = Logger(javaClass)

    override fun transform(outputs: TransformOutputs) {
        runCatching {
            val path = inputArtifact.asPath
            val name = path.nameWithoutExtension.removeSuffix(".tar")
            val targetDirectory = outputs.dir(name).toPath()

            parameters.extractorService.get().extract(path, targetDirectory)
        }.onFailure {
            log.error("${javaClass.canonicalName} execution failed.", it)
        }
    }

    companion object {
        internal fun register(dependencies: DependencyHandler, extractorServiceProvider: Provider<ExtractorService>) {
            dependencies.registerTransform(ExtractorTransformer::class) {
                from.attribute(Attributes.extracted, false)
                to.attribute(Attributes.extracted, true)

                parameters {
                    extractorService = extractorServiceProvider
                }
            }
        }
    }
}
