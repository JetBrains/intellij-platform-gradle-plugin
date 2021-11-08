package org.jetbrains.intellij.tasks

import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.TaskAction
open class SetupDependenciesTask : ConventionTask() {

    @TaskAction
    fun setupDependencies() {
    }
}
