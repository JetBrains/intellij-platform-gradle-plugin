// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal

interface SandboxAware : IntelliJPlatformVersionAware {

    /**
     * Represents the suffix used for test-related configuration.
     */
    @get:Internal
    val sandboxSuffix: Property<String>

    @get:Internal
    val sandboxDirectory: DirectoryProperty

    @get:Internal
    val sandboxConfigDirectory: DirectoryProperty

    @get:Internal
    val sandboxPluginsDirectory: DirectoryProperty

    @get:Internal
    val sandboxSystemDirectory: DirectoryProperty

    @get:Internal
    val sandboxLogDirectory: DirectoryProperty
}
