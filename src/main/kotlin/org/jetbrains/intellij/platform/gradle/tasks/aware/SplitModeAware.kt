// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
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
interface SplitModeAware : IntelliJPlatformVersionAware {

    /**
     * Enables Split Mode when running the IDE.
     *
     * Default value: `false`
     */
    @get:Internal
    val splitMode: Property<Boolean>

    /**
     * Specifies in which part of the product the developed plugin should be installed.
     * 
     * Default value: [SplitModeTarget.BACKEND]
     */
    @get:Internal
    val splitModeTarget: Property<SplitModeTarget>

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
        BACKEND_AND_FRONTEND;

        override fun toString() = name.lowercase().replace('_', '-')
    }
}
