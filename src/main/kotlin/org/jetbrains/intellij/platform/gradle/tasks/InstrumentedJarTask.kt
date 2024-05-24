// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.Constants.INSTRUMENT_CODE
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.compaion.JarCompanion

abstract class InstrumentedJarTask : Jar() {

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<InstrumentedJarTask>(Tasks.INSTRUMENTED_JAR) {
                val extension = project.the<IntelliJPlatformExtension>()
                val instrumentCodeEnabled = extension.instrumentCode

                val instrumentCodeTaskProvider = project.tasks.named<InstrumentCodeTask>(INSTRUMENT_CODE)
                val jarTaskProvider = project.tasks.named<Jar>(Tasks.External.JAR)

                from(instrumentCodeTaskProvider)
                from(project.zipTree(jarTaskProvider.map { it.archiveFile }))

                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                archiveClassifier.convention("instrumented")

                JarCompanion.applyPluginManifest(this)
                JarCompanion.configureConditionalArtifact(this, jarTaskProvider, instrumentCodeEnabled)
            }
    }
}
