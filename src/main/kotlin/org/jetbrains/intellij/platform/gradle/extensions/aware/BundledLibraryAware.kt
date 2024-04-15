// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.aware

import org.gradle.api.artifacts.Dependency
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.extensions.DependencyAction
import org.jetbrains.intellij.platform.gradle.utils.platformPath

interface BundledLibraryAware : DependencyAware, IntelliJPlatformAware {
    val objects: ObjectFactory
}

/**
 * Adds a dependency on a Jar bundled within the current IntelliJ Platform.
 *
 * @param pathProvider Path to the bundled Jar file, like `lib/testFramework.jar`
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action The action to be performed on the dependency. Defaults to an empty action.
 */
internal fun BundledLibraryAware.addBundledLibrary(
    pathProvider: Provider<String>,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.addLater(
    pathProvider.map { path -> createBundledLibraryDependency(path).apply(action) }
)

/**
 * Creates a [Dependency] using a Jar file resolved in [platformPath] with [path].
 */
internal fun BundledLibraryAware.createBundledLibraryDependency(path: String) =
    dependencies.create(objects.fileCollection().from(platformPath.resolve(path)))
