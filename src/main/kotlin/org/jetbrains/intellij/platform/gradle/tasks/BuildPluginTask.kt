// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.named
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks

/**
 * This class represents a task for building a plugin and preparing a ZIP archive for deployment.
 *
 * It uses the content produced by [PrepareSandboxTask] and [JarSearchableOptionsTask] tasks as an input.
 */
@DisableCachingByDefault(because = "Zip based tasks do not benefit from caching")
abstract class BuildPluginTask : Zip() {

    init {
        group = PLUGIN_GROUP_NAME
        description = "Assembles plugin and prepares ZIP archive for deployment."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<BuildPluginTask>(Tasks.BUILD_PLUGIN) {
                val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)
                val jarSearchableOptionsTaskProvider = project.tasks.named<JarSearchableOptionsTask>(Tasks.JAR_SEARCHABLE_OPTIONS)

                archiveBaseName.convention(prepareSandboxTaskProvider.flatMap { it.pluginName })

                from(prepareSandboxTaskProvider.flatMap {
                    it.pluginName.map { pluginName ->
                        it.destinationDir.resolve(pluginName)
                    }
                })
                from(jarSearchableOptionsTaskProvider.flatMap { it.archiveFile }) {
                    into("lib")
                }
                into(prepareSandboxTaskProvider.flatMap { it.pluginName })

                dependsOn(jarSearchableOptionsTaskProvider)
                dependsOn(prepareSandboxTaskProvider)

//            project.artifacts.add(Dependency.ARCHIVES_CONFIGURATION, this).let { publishArtifact ->
//                extensions.getByType<DefaultArtifactPublicationSet>().addCandidate(publishArtifact)
//                project.components.add(IntelliJPlatformPluginLibrary())
//            }
            }
    }
}
