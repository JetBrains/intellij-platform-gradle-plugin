// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.plugins.PluginManager
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal

/**
 * Provides the information if the current task is executed in a module context.
 */
interface ModuleAware {

    /**
     * Indicates if the current project represents an IntelliJ Platform plugin module.
     *
     * Used to determine if the task should be executed in a plugin module context.
     *
     * Default value: [PluginManager.isModule]
     */
    @get:Internal
    val module: Property<Boolean>
}
