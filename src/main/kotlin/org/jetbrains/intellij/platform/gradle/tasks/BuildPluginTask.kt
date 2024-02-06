// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.named
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks

/**
 * A task responsible for building plugin and preparing a ZIP archive for testing and deployment.
 *
 * It takes the output of the [PrepareSandboxTask] task containing the built project with all its modules and dependencies,
 * and the output of [JarSearchableOptionsTask] task.
 *
 * The produced archive is stored in the [ProjectLayout.getBuildDirectory]/distributions/[archiveFile] file.
 * The [archiveFile] name and location can be controlled with properties provided with the [Zip] base task.
 * By default, the [archiveBaseName] is set to the value of [PrepareSandboxTask.pluginName].
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
                into(archiveBaseName)

                dependsOn(jarSearchableOptionsTaskProvider)
                dependsOn(prepareSandboxTaskProvider)

//            project.artifacts.add(Dependency.ARCHIVES_CONFIGURATION, this).let { publishArtifact ->
//                extensions.getByType<DefaultArtifactPublicationSet>().addCandidate(publishArtifact)
//                project.components.add(IntelliJPlatformPluginLibrary())
//            }
            }
    }
}
