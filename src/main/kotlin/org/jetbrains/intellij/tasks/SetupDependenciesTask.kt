// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.dependency.IdeaDependency

abstract class SetupDependenciesTask : DefaultTask() {

    /**
     * Reference to the resolved `idea` dependency.
     */
    @get:Internal
    abstract val idea: Property<IdeaDependency>

    @TaskAction
    fun setupDependencies() {
    }
}
