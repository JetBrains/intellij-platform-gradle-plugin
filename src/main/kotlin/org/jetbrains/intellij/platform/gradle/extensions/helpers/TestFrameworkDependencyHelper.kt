// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.helpers

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.DependencyAction
import org.jetbrains.intellij.platform.gradle.extensions.createBundledLibraryDependency
import org.jetbrains.intellij.platform.gradle.extensions.createTestFrameworkDependency
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import java.nio.file.Path

internal class TestFrameworkDependencyHelper(
    private val configurations: ConfigurationContainer,
    private val dependencies: DependencyHandler,
    private val layout: ProjectLayout,
    private val objects: ObjectFactory,
    private val platformPath: Provider<Path>,
    private val productInfo: Provider<ProductInfo>,
    private val providers: ProviderFactory,
    private val repositories: RepositoryHandler,
    private val settingsRepositories: RepositoryHandler,
) {

    /**
     * Adds a dependency on the `test-framework` library required for testing plugins.
     *
     * In rare cases, when the presence of bundled `lib/testFramework.jar` library is necessary,
     * it is possible to attach it by using the [TestFrameworkType.Platform.Bundled] type.
     *
     * This dependency belongs to IntelliJ Platform repositories.
     *
     * @param typeProvider The TestFramework type provider.
     * @param versionProvider The version of the TestFramework.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addTestFrameworkDependency(
        typeProvider: Provider<TestFrameworkType>,
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        typeProvider.zip(versionProvider) { type, version ->
            when (type) {
                TestFrameworkType.Platform.Bundled ->
                    dependencies
                        .createBundledLibraryDependency(type.coordinates.artifactId, objects, platformPath.get())
                        .apply(action)

                else ->
                    dependencies
                        .createTestFrameworkDependency(type, version, layout, platformPath.get(), productInfo.get(), providers, repositories, settingsRepositories)
                        .apply(action)
            }
        },
    )
}
