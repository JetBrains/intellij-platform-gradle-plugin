// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.deleteQuietly
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.asPath

@DisableCachingByDefault(because = "Deletion cannot be cached")
abstract class SetupInstrumentCodeTask : DefaultTask() {

    /**
     * A flag that controls whether code instrumentation is enabled.
     *
     * Default value: [org.jetbrains.intellij.IntelliJPluginExtension.instrumentCode]
     */
    @get:Input
    abstract val instrumentationEnabled: Property<Boolean>

    /**
     * The path to the directory where instrumented classes will be saved.
     *
     * Default value: ${project.buildDir}/instrumented
     */
    @get:Internal
    abstract val instrumentedDir: DirectoryProperty

    init {
        group = PLUGIN_GROUP_NAME
        description = "Prepares code instrumentation tasks."
    }

    @TaskAction
    fun setupInstrumentCode() {
        instrumentedDir.get()
            .asPath
            .run {
                if (!instrumentationEnabled.get()) {
                    deleteQuietly()
                }
            }
    }
}
