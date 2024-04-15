// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.aware

import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.extensions.DependencyAction

interface JetBrainsRuntimeDependencyAware : DependencyAware

/**
 * A base method for adding a dependency on JetBrains Runtime.
 *
 * @param explicitVersionProvider The provider for the explicit version of the JetBrains Runtime.
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action The action to be performed on the dependency. Defaults to an empty action.
 */
internal fun JetBrainsRuntimeDependencyAware.addJetBrainsRuntimeDependency(
    explicitVersionProvider: Provider<String>,
    configurationName: String = Configurations.JETBRAINS_RUNTIME_DEPENDENCY,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.addLater(
    explicitVersionProvider.map {
        dependencies.create(
            group = "com.jetbrains",
            name = "jbr",
            version = it,
            ext = "tar.gz",
        ).apply(action)
    },
)
