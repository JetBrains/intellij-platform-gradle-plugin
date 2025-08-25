// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import com.jetbrains.plugin.structure.base.utils.exists
import com.jetbrains.plugin.structure.base.utils.listFiles
import org.gradle.api.Incubating
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.newInstance
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesHelper
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependencyConfiguration
import org.jetbrains.intellij.platform.gradle.services.ExtractorService
import org.jetbrains.intellij.platform.gradle.services.RequestedIntelliJPlatform
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import java.nio.file.Path
import java.util.*

/**
 * Resolves IntelliJ Platform dependencies from configured repositories and extracts them to a shared cache directory.
 * This resolver helps avoid Gradle's transform cache issues where multiple copies of IDE distributions are created
 * due to build classpath changes.
 * It provides a custom caching mechanism that reuses extracted IDE content across different build configurations.
 *
 * See: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1601
 *
 * This class is marked as incubating and may undergo changes in future versions.
 */
@Incubating
class IntelliJPlatformCacheResolver internal constructor(
    private val parameters: Parameters,
    private val configurations: ConfigurationContainer,
    private val dependenciesHelperProvider: Provider<IntelliJPlatformDependenciesHelper>,
    private val extractorService: Provider<ExtractorService>,
    private val objects: ObjectFactory,
) {

    interface Parameters {

        /**
         * A directory property for storing cached data during the build process.
         *
         * This property is used to specify the directory where intermediate or temporary files
         * will be saved to improve performance and avoid redundant processing.
         */
        val cacheDirectory: DirectoryProperty

        /**
         * Constructs the name for the specified IntelliJ Platform extracted directory.
         */
        val name: Property<(RequestedIntelliJPlatform) -> String>
    }

    /**
     * Resolves an IntelliJ Platform dependency based on the specified type, version, and installation preferences.
     *
     * This method converts the string type to an [IntelliJPlatformType] and creates providers for all parameters
     * before delegating to the main resolve implementation.
     *
     * @param type The IntelliJ Platform type as a string (e.g., "IC", "IU", "CL", etc.)
     * @param version The version of the IntelliJ Platform to resolve
     * @param configure IntelliJ Platform dependency configuration.
     * @return The path to the resolved IntelliJ Platform
     */
    @JvmOverloads
    fun resolve(
        type: Any,
        version: String,
        configure: IntelliJPlatformDependencyConfiguration.() -> Unit = {},
    ) = resolve {
        this.type = type.toIntelliJPlatformType()
        this.version = version
        configure()
    }

    /**
     * Resolves an IntelliJ Platform dependency based on the specified type, version, and installation preferences.
     *
     * This method converts the string type to an [IntelliJPlatformType] and creates providers for all parameters
     * before delegating to the main resolve implementation.
     *
     * @param type The IntelliJ Platform type as a string (e.g., "IC", "IU", "CL", etc.)
     * @param version The version of the IntelliJ Platform to resolve
     * @param configure IntelliJ Platform dependency configuration.
     * @return The path to the resolved IntelliJ Platform
     */
    @JvmOverloads
    fun resolve(
        type: Any,
        version: Provider<String>,
        configure: IntelliJPlatformDependencyConfiguration.() -> Unit = {},
    ) = resolve {
        this.type = type.toIntelliJPlatformType()
        this.version = version
        configure()
    }

    /**
     * Resolves an IntelliJ Platform dependency based on the specified type, version, and installation preferences.
     *
     * This method converts the string type to an [IntelliJPlatformType] and creates providers for all parameters
     * before delegating to the main resolve implementation.
     *
     * @param type The IntelliJ Platform type as a string (e.g., "IC", "IU", "CL", etc.)
     * @param version The version of the IntelliJ Platform to resolve
     * @param configure IntelliJ Platform dependency configuration.
     * @return The path to the resolved IntelliJ Platform
     */
    @JvmOverloads
    fun resolve(
        type: Provider<*>,
        version: Provider<String>,
        configure: IntelliJPlatformDependencyConfiguration.() -> Unit = {},
    ) = resolve {
        this.type = type.toIntelliJPlatformType()
        this.version = version
        configure()
    }

    /**
     * Resolves an IntelliJ Platform dependency based on the specified type provider, version provider, and installation preferences provider.
     *
     * This is the main implementation method that all other resolve methods delegate to. It creates a unique configuration name,
     * gets the [IntelliJPlatformDependenciesHelper], and creates a request for the IntelliJ Platform with the specified parameters.
     *
     * @param configure IntelliJ Platform dependency configuration.
     * @return The path to the resolved IntelliJ Platform
     */
    fun resolve(configure: IntelliJPlatformDependencyConfiguration.() -> Unit): Path {
        val dependenciesHelper = dependenciesHelperProvider.get()
        val requestedProvider = dependenciesHelper.requestedIntelliJPlatforms.set(
            objects.newInstance<IntelliJPlatformDependencyConfiguration>(objects).apply(configure)
        )

        val targetDirectory = parameters.cacheDirectory.dir(
            requestedProvider.map {
                parameters.name.get().invoke(it)
            },
        ).asPath
        if (targetDirectory.exists() && targetDirectory.listFiles().isNotEmpty()) {
            return targetDirectory
        }

        val configurationName = "${Configurations.INTELLIJ_PLATFORM_DEPENDENCY}_${UUID.randomUUID()}"
        val configuration = configurations.create(configurationName)

        dependenciesHelper.addIntelliJPlatformDependency(
            requestedIntelliJPlatformProvider = requestedProvider,
            configurationName = configuration.name,
        )

        extractorService.get().extract(
            path = configuration.files.single().toPath(),
            targetDirectory = targetDirectory,
        )

        return targetDirectory
    }
}
