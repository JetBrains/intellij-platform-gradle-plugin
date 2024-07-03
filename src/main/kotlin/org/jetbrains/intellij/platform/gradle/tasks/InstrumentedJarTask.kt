// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.INSTRUMENT_CODE
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.compaion.JarCompanion

@CacheableTask
abstract class InstrumentedJarTask : Jar() {

    init {
        group = Plugin.GROUP_NAME
        description = "Creates a JAR file with instrumented classes."
    }

    companion object : Registrable {
        private const val CLASSIFIER = "instrumented"

        override fun register(project: Project) =
            project.registerTask<InstrumentedJarTask>(Tasks.INSTRUMENTED_JAR) {
                val instrumentCodeTaskProvider = project.tasks.named<InstrumentCodeTask>(INSTRUMENT_CODE)
                val jarTaskProvider = project.tasks.named<Jar>(Tasks.External.JAR)

                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                archiveClassifier.convention(CLASSIFIER)
                JarCompanion.applyPluginManifest(this)

                from(instrumentCodeTaskProvider)
                from(project.zipTree(jarTaskProvider.map { it.archiveFile }))
            }
    }
}
