// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.models.validateSupportedVersion
import org.jetbrains.intellij.platform.gradle.utils.platformPath
import java.nio.file.Path

/**
 * Provides a task with the possibility of accessing information about the IntelliJ Platform currently used in the project.
 *
 * The [intelliJPlatformConfiguration] input property receives a dependency added to the [Configurations.INTELLIJ_PLATFORM_DEPENDENCY] configuration,
 * which eventually is resolved and lets to access the IntelliJ Platform details such as [ProductInfo] or the path to the IntelliJ Platform directory.
 *
 * It is required to have a dependency on the IntelliJ Platform added to the project with helpers available in [IntelliJPlatformDependenciesExtension].
 *
 * @see IntelliJPlatformDependenciesExtension
 */
interface IntelliJPlatformVersionAware {

    /**
     * Holds the [Configurations.INTELLIJ_PLATFORM_DEPENDENCY] configuration with the IntelliJ Platform dependency added.
     * It should not be directly accessed.
     *
     * @see platformPath
     * @see productInfo
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val intelliJPlatformConfiguration: ConfigurableFileCollection

    /**
     * Holds the [Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY] configuration with the optional custom IntelliJ Platform plugins dependencies added.
     * It should not be directly accessed.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val intelliJPlatformPluginConfiguration: ConfigurableFileCollection

    /**
     * Holds the [Configurations.INTELLIJ_PLATFORM_TEST_RUNTIME_FIX_CLASSPATH] configuration with the custom IntelliJ Platform core dependency required for running tests..
     * It should not be directly accessed.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val intelliJPlatformTestRuntimeFixClasspathConfiguration: ConfigurableFileCollection

    /**
     * Provides access to the IntelliJ Platform dependency artifact path.
     */
    @get:Internal
    val platformPath: Path
        get() = intelliJPlatformConfiguration.platformPath()

    /**
     * Provides information about the IntelliJ Platform product.
     * The information is retrieved from the `product-info.json` file in the IntelliJ Platform directory.
     *
     * @see ProductInfo
     */
    @get:Internal
    val productInfo: ProductInfo
        get() = platformPath.productInfo()

    /**
     * Validates that the resolved IntelliJ Platform is supported by checking against the minimal supported IntelliJ Platform version.
     *
     * @see ProductInfo.validateSupportedVersion
     */
    @Throws(IllegalArgumentException::class)
    fun validateIntelliJPlatformVersion() = productInfo.validateSupportedVersion()
}
