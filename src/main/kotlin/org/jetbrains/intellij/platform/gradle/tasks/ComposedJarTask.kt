// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.CacheableTask
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.intellij.platform.gradle.Constants.Components
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.companion.JarCompanion
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider

/**
 * Composes a final Jar archive by combining the output of base [Tasks.External.JAR] or [Tasks.INSTRUMENTED_JAR] tasks,
 * depending on if code instrumentation is enabled with [IntelliJPlatformExtension.instrumentCode].
 *
 * The final Jar is also combined with plugin modules marked using the [IntelliJPlatformDependenciesExtension.pluginModule] dependencies helper.
 *
 * To understand what is going on in this class read and watch:
 * - [Understanding Gradle #13 – Aggregating Custom Artifacts](https://youtu.be/2gPJD0mAres?t=461)
 * - [Understanding Gradle #12 – Publishing Libraries](https://www.youtube.com/watch?v=8z5KFCLZDd0)
 * - [Working with Variant Attributes](https://docs.gradle.org/current/userguide/variant_attributes.html#sec:standard_attributes)
 * - [Variant-aware sharing of artifacts between projects](https://docs.gradle.org/current/userguide/cross_project_publications.html#sec:variant-aware-sharing)
 */
@CacheableTask
abstract class ComposedJarTask : Jar() {

    init {
        group = Plugin.GROUP_NAME
        description = "Composes a final Jar archive by combining the base jar, and instrumented classes, and declared submodules."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<ComposedJarTask>(Tasks.COMPOSED_JAR) {
                val softwareComponentFactory = project.serviceOf<SoftwareComponentFactory>()
                val jarTaskProvider = project.tasks.named<Jar>(Tasks.External.JAR)
                val instrumentedJarTaskProvider = project.tasks.named<Jar>(Tasks.INSTRUMENTED_JAR)
                val intellijPlatformPluginModuleConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE]
                val intellijPlatformComposedJarConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_COMPOSED_JAR]
                val intellijPlatformComposedJarApiConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_COMPOSED_JAR_API]

                val sourceTaskProvider = project.extensionProvider.flatMap {
                    it.instrumentCode.flatMap { value ->
                        when (value) {
                            true -> instrumentedJarTaskProvider
                            false -> jarTaskProvider
                        }
                    }
                }

                from(project.zipTree(sourceTaskProvider.flatMap { it.archiveFile }))
                from(project.provider {
                    intellijPlatformPluginModuleConfiguration.map {
                        project.zipTree(it)
                    }
                })

                dependsOn(sourceTaskProvider)
                dependsOn(intellijPlatformPluginModuleConfiguration)

                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                JarCompanion.applyPluginManifest(this)

                intellijPlatformComposedJarConfiguration.outgoing.artifact(this)
                intellijPlatformComposedJarApiConfiguration.outgoing.artifact(this)

                softwareComponentFactory.adhoc(Components.INTELLIJ_PLATFORM).apply {
                    project.components.add(this)
                    addVariantsFromConfiguration(intellijPlatformComposedJarConfiguration) {
                        mapToMavenScope("runtime")
                    }
                }
            }
    }
}
