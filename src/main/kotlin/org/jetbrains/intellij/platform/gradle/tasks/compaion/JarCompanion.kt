// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.compaion

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.GenerateManifestTask
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.tasks.registerTask
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider

class JarCompanion {

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<Jar>(Tasks.External.JAR, configureWithType = false) {
                archiveBaseName.convention(project.extensionProvider.flatMap { it.projectName })
                archiveClassifier.convention("base")
                applyPluginManifest(this)

                exclude("**/classpath.index")
            }

        fun <T : Jar> applyPluginManifest(task: T) {
            val generateManifestTaskProvider = task.project.tasks.named<GenerateManifestTask>(Tasks.GENERATE_MANIFEST)
            task.manifest.from(generateManifestTaskProvider.flatMap { it.generatedManifest })
            task.dependsOn(generateManifestTaskProvider) // TODO: remove when fixed: https://github.com/gradle/gradle/issues/25435
        }
    }
}
