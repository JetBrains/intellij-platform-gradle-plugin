// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.aware

import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.extensions.DependencyAction
import org.jetbrains.intellij.platform.gradle.extensions.createPluginVerifierDependency

interface PluginVerifierDependencyAware : DependencyAware

/**
 * A base method for adding  a dependency on IntelliJ Plugin Verifier.
 *
 * @param versionProvider The provider of the IntelliJ Plugin Verifier version.
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action The action to be performed on the dependency. Defaults to an empty action.
 */
internal fun PluginVerifierDependencyAware.addPluginVerifierDependency(
    versionProvider: Provider<String>,
    configurationName: String = Configurations.INTELLIJ_PLUGIN_VERIFIER,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.addLater(
    versionProvider.map { version ->
        dependencies
            .createPluginVerifierDependency(version)
            .apply(action)
    },
)
