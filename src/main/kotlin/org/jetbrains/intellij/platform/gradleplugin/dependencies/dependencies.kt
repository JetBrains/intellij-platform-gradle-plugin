// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.dependencies

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.newInstance
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Dependencies
import org.jetbrains.intellij.platform.gradleplugin.asPath
import org.jetbrains.intellij.platform.gradleplugin.model.ProductInfo
import kotlin.io.path.pathString

internal typealias DependencyAction = (Dependency.(settings: IntelliJPlatformDependencySettings) -> Unit)

interface IntelliJPlatformDependencySettings {

    val ivyDirectory: Property<String>
}

internal fun DependencyHandler.applyIntelliJPlatformSettings(objects: ObjectFactory, providers: ProviderFactory, layout: ProjectLayout) {
    val settings = objects.newInstance(IntelliJPlatformDependencySettings::class)

    settings.ivyDirectory.convention(layout.projectDirectory.asPath.resolve(".gradle").resolve("intellijPlatform").resolve("ivy").pathString)

    (this as ExtensionAware).extensions.add(IntelliJPluginConstants.INTELLIJ_PLATFORM_DEPENDENCY_SETTINGS_NAME, settings)
}

internal val DependencyHandler.intellijPlatformDependencySettings: IntelliJPlatformDependencySettings
    get() = (this as ExtensionAware).extensions.getByType<IntelliJPlatformDependencySettings>()


internal fun DependencyHandler.create(
    type: IntelliJPlatformType,
    version: String,
    settings: IntelliJPlatformDependencySettings = intellijPlatformDependencySettings,
    action: DependencyAction = {},
) = create(
    group = type.groupId,
    name = type.artifactId,
    version = version,
).apply {
    action(this, settings)
}

internal fun DependencyHandler.create(
    productInfo: ProductInfo,
    settings: IntelliJPlatformDependencySettings = intellijPlatformDependencySettings,
    action: DependencyAction = {},
) = create(
    group = Dependencies.INTELLIJ_PLATFORM_LOCAL_GROUP,
    name = productInfo.productCode,
    version = productInfo.version,
).apply {
    action(this, settings)
}
