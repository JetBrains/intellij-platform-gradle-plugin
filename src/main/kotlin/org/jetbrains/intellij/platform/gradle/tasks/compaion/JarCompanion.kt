// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.compaion

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.GenerateManifestTask
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.tasks.registerTask
import org.jetbrains.intellij.platform.gradle.utils.asPath

class JarCompanion {
    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<Jar>(Tasks.External.JAR, configureWithType = false) {
                val extension = project.the<IntelliJPlatformExtension>()

                exclude("**/classpath.index")

                archiveBaseName.convention(extension.projectName)

                applyPluginManifest(this)

                val runtimeElementsConfiguration = project.configurations[Configurations.External.RUNTIME_ELEMENTS]
                runtimeElementsConfiguration.artifacts.removeIf { artifact ->
                    artifact.file == archiveFile.get().asFile
                }

                project.artifacts.add(runtimeElementsConfiguration.name, archiveFile)
            }

        fun <J : Jar> applyPluginManifest(task: J) = with(task) {
            val generateManifestTaskProvider = project.tasks.named<GenerateManifestTask>(Tasks.GENERATE_MANIFEST)

            manifest.from(generateManifestTaskProvider.flatMap { it.generatedManifest })

            dependsOn(generateManifestTaskProvider) // TODO: remove when fixed: https://github.com/gradle/gradle/issues/25435
        }

        fun <J : Jar> configureConditionalArtifact(task: J, fallbackJarProvider: Provider<J>, condition: Provider<Boolean>) = with(task) {
            val runtimeElementsConfiguration = project.configurations[Configurations.External.RUNTIME_ELEMENTS]
            runtimeElementsConfiguration.artifacts.removeIf { artifact ->
                artifact.file == fallbackJarProvider.flatMap { it.archiveFile }.asPath.toFile()
            }

            onlyIf { condition.get() }

            project.artifacts.add(runtimeElementsConfiguration.name, condition.map { enabled ->
                when {
                    enabled -> this
                    else -> fallbackJarProvider.get()
                }
            })
        }
    }
}
