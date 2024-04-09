// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal

/**
 * The interface provides the possibility to auto-reload plugin when run in the IDE.
 */
interface AutoReloadAware {

    /**
     * Enables auto-reload of dynamic plugins.
     * Dynamic plugin will be reloaded automatically when its content is modified.
     * This allows a much faster development cycle by avoiding a full restart of the development instance after code changes.
     *
     * Default value: `true`
     *
     * @see RunnableIdeAware
     */
    @get:Internal
    val autoReload: Property<Boolean>
}
