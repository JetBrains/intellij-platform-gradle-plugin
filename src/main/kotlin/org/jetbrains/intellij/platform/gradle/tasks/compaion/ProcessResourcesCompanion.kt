// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.compaion

import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.kotlin.dsl.named
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.tasks.registerTask

class ProcessResourcesCompanion {
    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<ProcessResources>(Tasks.External.PROCESS_RESOURCES) {
                val patchPluginXmlTaskProvider = project.tasks.named<PatchPluginXmlTask>(Tasks.PATCH_PLUGIN_XML)

                from(patchPluginXmlTaskProvider) {
                    duplicatesStrategy = DuplicatesStrategy.INCLUDE
                    into("META-INF")
                }
            }
    }
}
