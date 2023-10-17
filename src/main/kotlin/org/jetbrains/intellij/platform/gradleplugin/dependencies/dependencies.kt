// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.dependencies

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.newInstance
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Extensions
import org.jetbrains.kotlin.gradle.utils.projectCacheDir

internal typealias DependencyAction = (Dependency.(settings: IntelliJPlatformDependencySettings) -> Unit)

interface IntelliJPlatformDependencySettings {

    val ivyDirectory: DirectoryProperty
}

internal fun DependencyHandler.applyIntelliJPlatformSettings(objects: ObjectFactory, gradle: Gradle) {
    val settings = objects.newInstance(IntelliJPlatformDependencySettings::class)

    settings.ivyDirectory.dir(gradle.projectCacheDir.resolve("intellijPlatform/ivy").path)

    (this as ExtensionAware).extensions.add(Extensions.INTELLIJ_PLATFORM_DEPENDENCY_SETTINGS, settings)
}

internal val DependencyHandler.intellijPlatformDependencySettings: IntelliJPlatformDependencySettings
    get() = (this as ExtensionAware).extensions.getByType<IntelliJPlatformDependencySettings>()
