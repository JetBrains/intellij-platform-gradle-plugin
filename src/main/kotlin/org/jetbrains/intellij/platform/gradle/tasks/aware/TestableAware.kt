// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.PrepareTestTask
import org.jetbrains.intellij.platform.gradle.tasks.TestIdeTask

/**
 * Interface used to describe tasks used for running tests, such as a customizable [TestIdeTask] or [PrepareTestTask]
 * used for configuring `test` and keeping it immutable.
 */
interface TestableAware : CoroutinesJavaAgentAware, RuntimeAware, SandboxAware {

    /**
     * Holds the [Configurations.INTELLIJ_PLATFORM_CLASSPATH] configuration.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val intellijPlatformClasspathConfiguration: ConfigurableFileCollection

    /**
     * Holds the [Configurations.INTELLIJ_PLATFORM_TEST_CLASSPATH] configuration, which by default contains
     * [Configurations.INTELLIJ_PLATFORM_DEPENDENCY], [Configurations.INTELLIJ_PLATFORM_DEPENDENCIES],
     * and [Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES] configurations.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val intellijPlatformTestClasspathConfiguration: ConfigurableFileCollection

    /**
     * Holds the [Configurations.INTELLIJ_PLATFORM_TEST_RUNTIME_CLASSPATH] configuration.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val intellijPlatformTestRuntimeClasspathConfiguration: ConfigurableFileCollection

    /**
     * Specifies the directory where the plugin artifacts are to be placed.
     *
     * Default value: [sandboxPluginsDirectory]/[IntelliJPlatformExtension.projectName]
     */
    @get:Internal
    val pluginDirectory: DirectoryProperty
}
