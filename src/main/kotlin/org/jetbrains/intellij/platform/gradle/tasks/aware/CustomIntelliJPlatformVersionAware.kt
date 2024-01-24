// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradle.utils.IntelliJPlatformType

/**
 * By default, the project with the IntelliJ Platform Gradle Plugin applied required the presence of the IntelliJ Platform, referred to later by various tasks,
 * configurations, and extensions.
 * The Custom IntelliJ Platform concept allows using another version, i.e., to run a guest IDE or tests against it.
 * When applying this interface to the task, custom configurations to hold new dependencies defined by [type] and [version]
 * (or [localPath], if referring to the local IntelliJ Platform instance) are created, as well as a dedicated [PrepareSandboxTask] task.
 * Configurations, as well as the task preparing sandbox for running and testing the custom IntelliJ Platform (if required), have a random suffix applied
 * to avoid collisions.
 */
interface CustomIntelliJPlatformVersionAware : IntelliJPlatformVersionAware {

    /**
     * An input property to configure the type of the custom IntelliJ Platform.
     *
     * By default, it refers to the IntelliJ Platform type used by the current project.
     *
     * @see IntelliJPlatformType
     */
    @get:Input
    @get:Optional
    val type: Property<IntelliJPlatformType>

    /**
     * An input property to configure the version of the custom IntelliJ Platform.
     *
     * By default, it refers to the IntelliJ Platform version used by the current project.
     */
    @get:Input
    @get:Optional
    val version: Property<String>

    /**
     * An input property to define the path to the local IntelliJ Platform instance to configure the version of the custom IntelliJ Platform.
     * The local path precedes the IntelliJ Platform resolution using the [type] and [version] properties.
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    val localPath: DirectoryProperty
}
