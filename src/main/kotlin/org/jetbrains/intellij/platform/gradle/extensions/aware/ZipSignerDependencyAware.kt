// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.aware

import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.extensions.DependencyAction
import org.jetbrains.intellij.platform.gradle.extensions.createMarketplaceZipSignerDependency

interface ZipSignerDependencyAware : DependencyAware

/**
 * A base method for adding a dependency on Marketplace ZIP Signer.
 *
 * @param versionProvider The provider of the Marketplace ZIP Signer version.
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action The action to be performed on the dependency. Defaults to an empty action.
 */
internal fun ZipSignerDependencyAware.addZipSignerDependency(
    versionProvider: Provider<String>,
    configurationName: String = Configurations.MARKETPLACE_ZIP_SIGNER,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.addLater(
    versionProvider.map { version ->
        dependencies
            .createMarketplaceZipSignerDependency(version)
            .apply(action)
    },
)
