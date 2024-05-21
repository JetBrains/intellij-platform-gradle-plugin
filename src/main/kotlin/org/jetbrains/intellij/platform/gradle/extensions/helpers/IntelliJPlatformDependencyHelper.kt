// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.helpers

import org.gradle.api.GradleException
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.extensions.*
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import java.io.File
import java.nio.file.Path

internal class IntelliJPlatformDependencyHelper(
    private val configurations: ConfigurationContainer,
    private val dependencies: DependencyHandler,
    private val providers: ProviderFactory,
    private val resources: ResourceHandler,
    private val rootProjectDirectory: Path,
) {

    /**
     * A base method for adding a dependency on IntelliJ Platform.
     *
     * @param typeProvider The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param versionProvider The provider for the version of the IntelliJ Platform dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action An optional action to be performed on the created dependency.
     * @throws GradleException
     */
    @Throws(GradleException::class)
    internal fun addIntelliJPlatformDependency(
        typeProvider: Provider<*>,
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        typeProvider.map { it.toIntelliJPlatformType() }.zip(versionProvider) { type, version ->
            when (type) {
                IntelliJPlatformType.AndroidStudio -> dependencies.createAndroidStudioDependency(version, providers, resources).apply(action)

                else -> dependencies.createIntelliJPlatformDependency(version, type, providers).apply(action)
            }
        },
    )

    /**
     * A base method for adding a dependency on a local IntelliJ Platform instance.
     *
     * @param localPathProvider The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action An optional action to be performed on the created dependency.
     * @throws GradleException
     */
    @Throws(GradleException::class)
    internal fun addIntelliJPlatformLocalDependency(
        localPathProvider: Provider<*>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_LOCAL,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        localPathProvider.map { localPath ->
            dependencies.createLocalIntelliJPlatformDependency(localPath, providers, rootProjectDirectory).apply(action)
        },
    )
}
