// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.PrepareTestTask
import org.jetbrains.intellij.platform.gradle.tasks.TestIdeTask

/**
 * Interface used to describe tasks used for running tests, such as a customizable [TestIdeTask] or [PrepareTestTask]
 * used for configuring `test` and keeping it immutable.
 */
interface TestableAware : CoroutinesJavaAgentAware, RuntimeAware, SandboxAware {

    /**
     * Specifies the directory where the plugin artifacts are to be placed.
     *
     * Default value: [sandboxPluginsDirectory]/[IntelliJPlatformExtension.projectName]
     */
    @get:Internal
    val pluginDirectory: DirectoryProperty
}
