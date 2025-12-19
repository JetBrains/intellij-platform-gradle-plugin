// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input

/**
 * Provides the possibility to auto-reload Compose UI when code changes in the IDE.
 */
interface ComposeHotReloadAware {
    /**
     * Enables auto-reload of Compose UIs after code changes for the task.
     * This allows a much faster development cycle by avoiding a full restart of the development instance after code changes.
     *
     * Default value: false
     */
    @get:Input
    val composeHotReload: Property<Boolean>

    /**
     * Hot Reload Agent configuration.
     */
    @get:Classpath
    val javaAgentConfiguration: ConfigurableFileCollection
}