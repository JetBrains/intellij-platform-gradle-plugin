// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.dependency.IdeaDependency
import org.jetbrains.intellij.platform.gradle.error
import org.jetbrains.intellij.platform.gradle.logCategory

/**
 * A deprecated method for setting up IntelliJ Platform dependencies.
 *
 * The `setupDependencies` task was automatically added to the ["After Sync" Gradle trigger](https://www.jetbrains.com/help/idea/work-with-gradle-tasks.html#config_triggers_gradle) to make the IntelliJ SDK dependency available for IntelliJ IDEA right after the Gradle synchronization.
 * With IntelliJ Platform Gradle Plugin 2.0 release, this method is no longer needed as the native Gradle dependencies resolution is in use.
 *
 * To remove any references to this task, call the "Tasks Activation" action and remove the `setupDependencies` entry from the "After Sync" group.
 *
 * TODO: Link to SDK Docs
 */
@DisableCachingByDefault(because = "No output state to track")
abstract class SetupDependenciesTask : DefaultTask() {

    /**
     * Reference to the resolved `idea` dependency.
     * TODO: suggest alternative method for accessing IDEA dependency
     */
    @Deprecated(message = "setupDependencies.idea is no longer available")
    @get:Internal
    abstract val idea: Property<IdeaDependency>

    private val context = logCategory()

    init {
        group = PLUGIN_GROUP_NAME
        description = "Deprecated task"
    }

    @TaskAction
    fun setupDependencies() {
        error(context, "Task is deprecated now, see: [SDK Docs link]")
    }
}
