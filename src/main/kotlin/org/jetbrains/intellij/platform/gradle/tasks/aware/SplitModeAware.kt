// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.validateSupportedVersion
import org.jetbrains.intellij.platform.gradle.utils.toVersion

/**
 * When you develop a plugin, you may want to check how it works in remote development mode, when one machine is running the backend part and another
 * is running a frontend part (JetBrains Client) which connects to the backend.
 *
 * This property allows running the IDE with backend and frontend parts running in separate processes.
 * The developed plugin is installed in the backend part.
 *
 * Split Mode requires the IntelliJ Platform in the version `241.14473` or later.
 */
interface SplitModeAware : IntelliJPlatformVersionAware, SandboxStructure {

    /**
     * Enables Split Mode when running the IDE.
     *
     * Default value: [IntelliJPlatformExtension.splitMode]
     */
    @get:Input
    val splitMode: Property<Boolean>

    /**
     * Specifies in which part of the product the developed plugin should be installed.
     *
     * Default value: [IntelliJPlatformExtension.splitModeTarget]
     */
    @get:Input
    val splitModeTarget: Property<SplitModeTarget>

    /**
     * A frontend configuration directory located within the [sandboxDirectory].
     *
     * @see Constants.Sandbox.CONFIG
     */
    @get:Internal
    val sandboxConfigFrontendDirectory: DirectoryProperty

    /**
     * A frontend plugins directory located within the [sandboxDirectory].
     *
     * @see Constants.Sandbox.PLUGINS
     */
    @get:Internal
    val sandboxPluginsFrontendDirectory: DirectoryProperty

    /**
     * A frontend system directory located within the [sandboxDirectory].
     *
     * @see Constants.Sandbox.SYSTEM
     */
    @get:Internal
    val sandboxSystemFrontendDirectory: DirectoryProperty

    /**
     * A frontend log directory located within the [sandboxDirectory].
     *
     * @see Constants.Sandbox.LOG
     */
    @get:Internal
    val sandboxLogFrontendDirectory: DirectoryProperty

    /**
     * Path to a properties file which will be used to configure the frontend process if the IDE is started in Split Mode.
     */
    @get:Internal
    val splitModeFrontendProperties: Provider<RegularFile>
        get() = sandboxDirectory.file("frontend.properties")

    /**
     * Validates that the resolved IntelliJ Platform supports Split Mode.
     *
     * @see ProductInfo.validateSupportedVersion
     * @throws IllegalArgumentException
     */
    @Throws(IllegalArgumentException::class)
    fun validateSplitModeSupport() {
        val currentBuildNumber = productInfo.buildNumber.toVersion()
        if (splitMode.get() && currentBuildNumber < Constraints.MINIMAL_SPLIT_MODE_BUILD_NUMBER) {
            throw IllegalArgumentException("Split Mode requires the IntelliJ Platform in version '${Constraints.MINIMAL_SPLIT_MODE_BUILD_NUMBER}' or later, but '$currentBuildNumber' was provided.")
        }
    }

    /**
     * Describes a part of the product where the developed plugin can be installed when running in [splitMode].
     */
    enum class SplitModeTarget {
        BACKEND,
        FRONTEND,
        BOTH;

        fun includes(target: SplitModeTarget) = this == target || this == BOTH
    }
}
