// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.info
import org.jetbrains.intellij.logCategory

@DisableCachingByDefault(because = "No output state to track")
abstract class SetupDependenciesTask : DefaultTask() {

    /**
     * Reference to the resolved `idea` dependency.
     */
    @get:Internal
    abstract val idea: Property<IdeaDependency>

    private val context = logCategory()

    init {
        group = PLUGIN_GROUP_NAME
        description = "Sets up required dependencies for building and running project."
    }

    @TaskAction
    fun setupDependencies() {
        info(context, "Setting up dependencies using: ${idea.get().classes}")
    }
}
