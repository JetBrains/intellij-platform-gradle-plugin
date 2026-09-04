// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.gradle.api.GradleException
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.registerTransform
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import javax.inject.Inject
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Produces an extracted plugin distribution with selected bundled libraries removed.
 *
 * Both sandbox installation and classpath collection consume this transform output, keeping their contents aligned.
 */
@CacheableTransform
internal abstract class PluginLibrariesFilterTransformer @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
) : TransformAction<PluginLibrariesFilterTransformer.Parameters> {

    interface Parameters : TransformParameters {

        /**
         * Bundled library file name patterns keyed by plugin ID.
         */
        @get:Input
        val exclusions: MapProperty<String, Set<String>>
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputArtifact: Provider<FileSystemLocation>

    private val log = Logger(javaClass)

    override fun transform(outputs: TransformOutputs) {
        runCatching {
            val input = inputArtifact.asPath
            val rules = parameters.exclusions.get()
            if (rules.isEmpty()) {
                outputs.dir(input)
                return@runCatching
            }

            val plugin = input.resolvePluginLayout()
            val exclusions = plugin
                ?.let { rules[it.id] }
                .orEmpty()

            if (plugin == null || exclusions.isEmpty()) {
                outputs.dir(input)
                return@runCatching
            }

            val pluginRoot = input.relativize(plugin.path)
            val prefix = pluginRoot
                .invariantSeparatorsPathString
                .takeIf(String::isNotEmpty)
                ?.plus('/')
                .orEmpty()
            val excludedPaths = exclusions.flatMap { pattern ->
                listOf(
                    "$prefix${Sandbox.Plugin.LIB}/$pattern",
                    "$prefix${Sandbox.Plugin.LIB_MODULES}/$pattern",
                )
            }
            val output = outputs.dir(input.fileName?.toString() ?: "filtered").toPath()

            fileSystemOperations.copy {
                from(input)
                into(output)
                exclude(excludedPaths)
            }

            val filteredPluginPath = output.resolve(pluginRoot)
            val filteredPlugin = filteredPluginPath.resolvePluginLayout()
            if (filteredPlugin?.id != plugin.id) {
                throw GradleException(
                    "Filtering bundled libraries from plugin '${plugin.id}' removed or invalidated its plugin descriptor. " +
                            "Narrow the exclusion patterns: ${exclusions.sorted().joinToString()}"
                )
            }
        }.onFailure {
            log.error("${javaClass.canonicalName} execution failed.", it)
        }.getOrThrow()
    }

    companion object {
        internal fun register(
            dependencies: DependencyHandler,
            exclusionsProvider: Provider<Map<String, Set<String>>>,
        ) {
            dependencies.registerTransform(PluginLibrariesFilterTransformer::class) {
                from
                    .attribute(Attributes.extracted, true)
                    .attribute(Attributes.collected, false)
                    .attribute(Attributes.pluginLibrariesFiltered, false)
                to
                    .attribute(Attributes.extracted, true)
                    .attribute(Attributes.collected, false)
                    .attribute(Attributes.pluginLibrariesFiltered, true)

                parameters.exclusions.set(exclusionsProvider)
            }
        }
    }
}
