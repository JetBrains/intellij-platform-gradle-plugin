// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradleplugin.dependency.IdeaDependency
import org.jetbrains.intellij.platform.gradleplugin.info
import org.jetbrains.intellij.platform.gradleplugin.logCategory

/**
 * Setups required dependencies for building and running project.
 *
 * This task is automatically added to the ["After Sync" Gradle trigger](https://www.jetbrains.com/help/idea/work-with-gradle-tasks.html#config_triggers_gradle) to make the IntelliJ SDK dependency available for IntelliJ IDEA right after the Gradle synchronization.
 *
 * > After removing the IntelliJ Platform Gradle Plugin from your project, the `Task 'setupDependencies' not found in root project` exception may occur.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#task-setupdependencies-not-found-in-root-project">Frequently Asked Questions</a>
 */
@DisableCachingByDefault(because = "No output state to track")
abstract class UnbindSetupDependenciesTask : DefaultTask() {

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
