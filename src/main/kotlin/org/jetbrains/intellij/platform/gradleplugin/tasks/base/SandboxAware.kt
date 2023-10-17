// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks.base

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginExtension

interface SandboxAware : PlatformVersionAware {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val sandboxDirectory: DirectoryProperty

    /**
     * Path to the `config` directory within the sandbox prepared with [PrepareSandboxTask].
     * Provided to the `idea.config.path` system property.
     *
     * Default value: [PrepareSandboxTask.configDir]
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val configDirectory: DirectoryProperty

    /**
     * Path to the `plugins` directory within the sandbox prepared with [PrepareSandboxTask].
     * Provided to the `idea.plugins.path` system property.
     *
     * Default value: [PrepareSandboxTask.getDestinationDir]
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val pluginsDirectory: DirectoryProperty

    /**
     * Path to the `system` directory within the sandbox prepared with [PrepareSandboxTask].
     * Provided to the `idea.system.path` system property.
     *
     * Default value: [IntelliJPluginExtension.sandboxDir]/system
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val systemDirectory: DirectoryProperty
}
