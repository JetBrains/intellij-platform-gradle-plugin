// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.base

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Sandbox
import org.jetbrains.intellij.platform.gradle.asPath
import kotlin.io.path.createDirectories

interface SandboxAware : PlatformVersionAware {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val sandboxDirectory: DirectoryProperty

    /**
     * Represents the suffix used for test-related configuration.
     */
    @get:Internal
    val sandboxSuffix: Property<String>

    @get:Internal
    val sandboxConfigDirectory
        get() = sandboxSuffix.flatMap { sandboxDirectory.dir(Sandbox.CONFIG + it).prepare() }

    @get:Internal
    val sandboxPluginsDirectory
        get() = sandboxSuffix.flatMap { sandboxDirectory.dir(Sandbox.PLUGINS + it).prepare() }

    @get:Internal
    val sandboxSystemDirectory
        get() = sandboxSuffix.flatMap { sandboxDirectory.dir(Sandbox.SYSTEM + it).prepare() }

    @get:Internal
    val sandboxLogDirectory
        get() = sandboxSuffix.flatMap { sandboxDirectory.dir(Sandbox.SYSTEM + it + "/log").prepare() }

    private fun Provider<Directory>.prepare() = also {
        it.asPath.createDirectories()
    }
}
