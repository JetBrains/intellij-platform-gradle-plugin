// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.base

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

interface SandboxAware : PlatformVersionAware {

    /**
     * Represents the suffix used for test-related configuration.
     */
    @get:Internal
    val sandboxSuffix: Property<String>

    @get:Internal
    val sandboxDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val sandboxConfigDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val sandboxPluginsDirectory: DirectoryProperty

    @get:OutputDirectory
    val sandboxSystemDirectory: DirectoryProperty

    @get:OutputDirectory
    val sandboxLogDirectory: DirectoryProperty
}
