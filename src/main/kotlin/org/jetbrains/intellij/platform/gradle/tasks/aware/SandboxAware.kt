// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

/**
 * The interface provides quick access to the sandbox container and specific directories located within it.
 * The path to the sandbox container is obtained using the [IntelliJPlatformExtension.sandboxContainer] extension property and the type and version
 * of the IntelliJ Platform applied to the project.
 *
 * @see IntelliJPlatformExtension.sandboxContainer
 */
interface SandboxAware : SandboxStructure {

    fun Task.applySandboxFrom(sandboxProducerTaskProvider: TaskProvider<PrepareSandboxTask>) {
        sandboxDirectory
            .convention(sandboxProducerTaskProvider.flatMap { it.sandboxDirectory })
            .finalizeValueOnRead()

        sandboxConfigDirectory
            .convention(sandboxProducerTaskProvider.flatMap { it.sandboxConfigDirectory })
            .finalizeValueOnRead()

        sandboxPluginsDirectory
            .convention(sandboxProducerTaskProvider.flatMap { it.sandboxPluginsDirectory })
            .finalizeValueOnRead()

        sandboxSystemDirectory
            .convention(sandboxProducerTaskProvider.flatMap { it.sandboxSystemDirectory })
            .finalizeValueOnRead()

        sandboxLogDirectory
            .convention(sandboxProducerTaskProvider.flatMap { it.sandboxLogDirectory })
            .finalizeValueOnRead()

        testSandbox
            .convention(sandboxProducerTaskProvider.flatMap { it.testSandbox })
            .finalizeValueOnRead()

        if (this is SplitModeAware) {
            splitMode
                .convention(sandboxProducerTaskProvider.flatMap { it.splitMode })
                .finalizeValueOnRead()

            splitModeTarget
                .convention(sandboxProducerTaskProvider.flatMap { it.splitModeTarget })
                .finalizeValueOnRead()
        }

        dependsOn(sandboxProducerTaskProvider)
    }
}
