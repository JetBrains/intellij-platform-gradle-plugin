// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.property
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import java.util.concurrent.ConcurrentHashMap

private const val baseConfigurationName = Configurations.INTELLIJ_PLATFORM_DEPENDENCY

/**
 * Manages requested IntelliJ Platform configurations by mapping configuration names
to corresponding platform definitions.
 *
 * This class allows dynamic creation and retrieval of `RequestedIntelliJPlatform` instances
 * based on configuration names, platform types, versions, and installer settings.
 *
 * @constructor Initializes the manager with the required provider and object factories
 * to support configuration and property management.
 *
 * @param providers An instance of `ProviderFactory` to create lazy-evaluated providers.
 * @param objects An instance of `ObjectFactory` to create property objects.
 */
class RequestedIntelliJPlatforms(private val providers: ProviderFactory, objects: ObjectFactory) {

    private val map = ConcurrentHashMap<String, Provider<RequestedIntelliJPlatform>>()
    private val base = objects.property<RequestedIntelliJPlatform>()

    /**
     * Sets the configuration for a requested IntelliJ Platform by associating the provided configuration name
     * with the corresponding platform parameters, including type, version, and installer settings.
     * If some of the parameters are not specified, the [base] configuration is used.
     *
     * @param configurationName The name of the configuration to set.
     * @param typeProvider A provider that supplies the IntelliJ Platform type for the given configuration.
     * @param versionProvider A provider that supplies the version of the IntelliJ Platform for the given configuration.
     * @param useInstallerProvider A provider that specifies whether to use the installer for the given configuration.
     */
    fun set(
        configurationName: String,
        typeProvider: Provider<*>,
        versionProvider: Provider<String>,
        useInstallerProvider: Provider<Boolean>
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
                    )
                )
            }

            else -> providers.provider {
                RequestedIntelliJPlatform(
                    type = typeProvider.map { it.toIntelliJPlatformType() }.orElse(base.map { it.type }).get(),
                    version = versionProvider.orElse(base.map { it.version }).get(),
                    installer = useInstallerProvider.orElse(base.map { it.installer }).get(),
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
 */
data class RequestedIntelliJPlatform(
    val type: IntelliJPlatformType,
    val version: String,
    val installer: Boolean,
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
