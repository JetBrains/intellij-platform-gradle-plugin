// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.helpers

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.extensions.DependencyAction
import org.jetbrains.intellij.platform.gradle.extensions.createJavaCompilerDependency
import org.jetbrains.intellij.platform.gradle.models.ProductInfo

internal class JavaCompilerDependencyHelper(
    private val configurations: ConfigurationContainer,
    private val dependencies: DependencyHandler,
    private val productInfo: Provider<ProductInfo>,
    private val providers: ProviderFactory,
    private val repositories: RepositoryHandler,
    private val settingsRepositories: RepositoryHandler,
) {

    /**
     * Adds a dependency on a Java Compiler used, i.e., for running code instrumentation.
     *
     * @param versionProvider The provider of the Java Compiler version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addJavaCompilerDependency(
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_JAVA_COMPILER,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        versionProvider.map { version ->
            dependencies
                .createJavaCompilerDependency(version, productInfo.get(), providers, repositories, settingsRepositories)
                .apply(action)
        }
    )
}
