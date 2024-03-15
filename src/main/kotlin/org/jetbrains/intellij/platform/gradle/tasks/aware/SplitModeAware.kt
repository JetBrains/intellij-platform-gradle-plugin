// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.validateSupportedVersion
import org.jetbrains.intellij.platform.gradle.utils.toVersion

/**
 * The interface provides the possibility to run IDE in Split Mode.
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
     * Validates that the resolved IntelliJ Platform supports Split Mode.
     *
     * @see ProductInfo.validateSupportedVersion
     */
    @Throws(IllegalArgumentException::class)
    fun validateSplitModeSupport() {
        val currentBuildNumber = productInfo.buildNumber.toVersion()
        if (splitMode.get() && currentBuildNumber < Constraints.MINIMAL_SPLIT_MODE_BUILD_NUMBER) {
            throw IllegalArgumentException("Split Mode requires the IntelliJ Platform in version '${Constraints.MINIMAL_SPLIT_MODE_BUILD_NUMBER}' or later, but '$currentBuildNumber' was provided.")
        }
    }
}
