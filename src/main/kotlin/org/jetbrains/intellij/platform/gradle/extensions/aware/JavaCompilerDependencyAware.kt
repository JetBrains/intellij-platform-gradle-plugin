// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.aware

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.VERSION_CURRENT
import org.jetbrains.intellij.platform.gradle.extensions.DependencyAction
import org.jetbrains.intellij.platform.gradle.extensions.urls
import org.jetbrains.intellij.platform.gradle.resolvers.closestVersion.JavaCompilerClosestVersionResolver

interface JavaCompilerDependencyAware : DependencyAware, IntelliJPlatformAware {
    val providers: ProviderFactory
    val repositories: RepositoryHandler
    val settingsRepositories: RepositoryHandler
}

/**
 * Adds a dependency on a Java Compiler used, i.e., for running code instrumentation.
 *
 * @param versionProvider The provider of the Java Compiler version.
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action The action to be performed on the dependency. Defaults to an empty action.
 */
internal fun JavaCompilerDependencyAware.addJavaCompilerDependency(
    versionProvider: Provider<String>,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_JAVA_COMPILER,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.addLater(
    versionProvider.map { version ->
        val resolveClosest = BuildFeature.USE_CLOSEST_VERSION_RESOLVING.getValue(providers).get()

        dependencies.create(
            group = "com.jetbrains.intellij.java",
            name = "java-compiler-ant-tasks",
            version = when (version) {
                VERSION_CURRENT -> when {
                    resolveClosest -> JavaCompilerClosestVersionResolver(
                        productInfo,
                        repositories.urls() + settingsRepositories.urls(),
                    ).resolve().version

                    else -> productInfo.buildNumber
                }

                else -> version
            },
        ).apply(action)
    }
)
