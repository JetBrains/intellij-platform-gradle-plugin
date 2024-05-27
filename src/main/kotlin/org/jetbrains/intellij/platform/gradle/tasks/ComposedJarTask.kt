// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.compaion.JarCompanion
import org.jetbrains.kotlin.gradle.utils.named

@CacheableTask
abstract class ComposedJarTask : Jar() {

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<ComposedJarTask>(Tasks.COMPOSED_JAR) {
                val extension = project.the<IntelliJPlatformExtension>()
                val jarTaskProvider = project.tasks.named<Jar>(Tasks.External.JAR)
                val instrumentedJarTaskProvider = project.tasks.named<Jar>(Tasks.INSTRUMENTED_JAR)
                val runtimeElementsConfiguration = project.configurations[Configurations.External.RUNTIME_ELEMENTS]
                val intellijPlatformPluginModuleConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE]

                val sourceTaskProvider = extension.instrumentCode.flatMap {
                    when (it) {
                        true -> instrumentedJarTaskProvider
                        false -> jarTaskProvider
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

                // Remove the default artifact exported by the current module and replace it with the final one provided by the task.
                runtimeElementsConfiguration.artifacts.removeIf { it.classifier == JarCompanion.CLASSIFIER }
                project.artifacts.add(runtimeElementsConfiguration.name, archiveFile)
            }
    }
}
