// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.property
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.ProductMode
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val baseConfigurationName = Configurations.INTELLIJ_PLATFORM_DEPENDENCY

abstract class RequestedIntelliJPlatformsService @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val providerFactory: ProviderFactory,
) : BuildService<BuildServiceParameters.None> {

    private val map = ConcurrentHashMap<String, Provider<RequestedIntelliJPlatform>>()
    private val base = objectFactory.property<RequestedIntelliJPlatform>()

    /**
     * Sets the configuration for a requested IntelliJ Platform by associating the provided configuration name
     * with the corresponding platform parameters, including type, version, and installer settings.
     * If some parameters are not specified, the [base] configuration is used.
     *
     * @param configurationName The name of the configuration to set.
     * @param typeProvider A provider that supplies the IntelliJ Platform type for the given configuration.
     * @param versionProvider A provider that supplies the version of the IntelliJ Platform for the given configuration.
     * @param useInstallerProvider A provider that specifies whether to use the installer for the given configuration.
     * @param productModeProvider A provider that specifies the product mode for the given configuration.
     */
    fun set(
        configurationName: String,
        typeProvider: Provider<*>,
        versionProvider: Provider<String>,
        useInstallerProvider: Provider<Boolean>,
        productModeProvider: Provider<ProductMode>,
    ) = requireNotNull(map.compute(configurationName) { key, previous ->
        when (key) {
            baseConfigurationName -> base.apply {
                check(previous == null) {
                    "The '$key' configuration already contains the following IntelliJ Platform dependency: ${previous?.get()}"
                }

                set(
                    RequestedIntelliJPlatform(
                        type = typeProvider.map { it.toIntelliJPlatformType() }.get(),
                        version = versionProvider.get(),
                        installer = useInstallerProvider.get(),
                        productMode = productModeProvider.get(),
                    )
                )
            }

            else -> providerFactory.provider {
                val errorProvider = { type: String ->
                    providerFactory.provider {
                        error("The '$key' configuration does not specify the $type of the IntelliJ Platform dependency nor can be resolved from the base configuration.")
                    }
                }

                RequestedIntelliJPlatform(
                    type = typeProvider
                        .map { it.toIntelliJPlatformType() }
                        .orElse(base.map { it.type })
                        .orElse(errorProvider("type"))
                        .get(),
                    version = versionProvider
                        .orElse(base.map { it.version })
                        .orElse(errorProvider("version")).
                        get(),
                    installer = useInstallerProvider
                        .orElse(base.map { it.installer })
                        .orElse(errorProvider("useInstaller"))
                        .get(),
                    productMode = productModeProvider
                        .orElse(base.map { it.productMode })
                        .orElse(errorProvider("productMode"))
                        .get(),
                )
            }
        }
    })

    /**
     * Retrieves the value associated with the given configuration name.
     * Throws an error if the configuration name does not exist in the map.
     *
     * @param configurationName The name of the configuration to retrieve.
     * @return The value corresponding to the provided configuration name.
     * @throws IllegalStateException if the configuration name is not found in the map.
     */
    operator fun get(configurationName: String) = map[configurationName]
        ?: error("Unknown configuration: $configurationName")
}

/**
 * Represents a requested IntelliJ Platform build with its type, version, and installation preference.
 *
 * This class encapsulates the details required to identify and describe a specific
 * IntelliJ Platform target for plugin development, dependency resolution, or plugin verification.
 *
 * @property type Defines the IntelliJ Platform type, such as IntelliJ IDEA, PyCharm, or other JetBrains IDEs.
 * @property version Specifies the version of the IntelliJ Platform to be used.
 * @property installer Indicates if the platform should include an installer.
 * @property productMode Indicates the mode in which the platform is being used.
 */
data class RequestedIntelliJPlatform(
    val type: IntelliJPlatformType,
    val version: String,
    val installer: Boolean,
    val productMode: ProductMode,
) {
    private val installerLabel = when (installer) {
        true -> "installer"
        else -> "non-installer"
    }

    /**
     * Indicates whether the current IntelliJ Platform is a nightly build.
     *
     * This variable checks if the base version matches a specific pattern
     * commonly used for nightly or snapshot builds, such as "123-SNAPSHOT" or "*-TRUNK-SNAPSHOT".
     */
    val isNightly
        get() = "(^|-)\\d{3}-SNAPSHOT|.*TRUNK-SNAPSHOT$".toRegex().matches(version)

    override fun toString() = "$type-$version ($installerLabel)"
}
