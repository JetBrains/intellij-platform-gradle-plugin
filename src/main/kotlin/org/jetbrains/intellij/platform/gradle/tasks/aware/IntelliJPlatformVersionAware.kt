// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.model.ProductInfo
import org.jetbrains.intellij.platform.gradle.model.assertSupportedVersion
import org.jetbrains.intellij.platform.gradle.model.productInfo
import java.nio.file.Path

/**
 * This interface provides tasks a possibility for accessing information about the IntelliJ Platform currently used in the project.
 * The [intelliJPlatformConfiguration] input property receives a dependency added to the [Configurations.INTELLIJ_PLATFORM] configuration,
 * which eventually is resolved and lets to access the IntelliJ Platform details such as [ProductInfo] or the path to the IntelliJ Platform directory.
 *
 * It is required to have a dependency on the IntelliJ Platform added to the project with helpers available in [IntelliJPlatformDependenciesExtension].
 *
 * @see IntelliJPlatformDependenciesExtension
 */
interface IntelliJPlatformVersionAware {

    /**
     * Holds the [Configurations.INTELLIJ_PLATFORM] configuration with the IntelliJ Platform dependency added.
     * It should not be directly accessed.
     *
     * @see platformPath
     * @see productInfo
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val intelliJPlatformConfiguration: ConfigurableFileCollection

    /**
     * Provides a direct path to the IntelliJ Platform dependency artifact.
     */
    @get:Internal
    val platformPath: Path
        get() = intelliJPlatformConfiguration.single().toPath()

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
     * Asserts that the resolved IntelliJ Platform is supported by checking against the minimal supported IntelliJ Platform version.
     *
     * @see ProductInfo.assertSupportedVersion
     */
    @Throws(IllegalArgumentException::class)
    fun assertIntelliJPlatformSupportedVersion() = productInfo.assertSupportedVersion()
}