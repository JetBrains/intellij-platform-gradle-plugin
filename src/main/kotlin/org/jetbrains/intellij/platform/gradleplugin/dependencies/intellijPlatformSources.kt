// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused")

package org.jetbrains.intellij.platform.gradleplugin.dependencies

import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType.IntellijIdeaCommunity
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType.PyCharmCommunity
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations

fun DependencyHandlerScope.intellijPlatformSources(
    type: IntelliJPlatformType?,
    version: String,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_SOURCES,
) = add(configurationName, createIntelliJPlatformSourcesDependency(type, version))

internal fun DependencyHandlerScope.intellijPlatformSources(
    type: IntelliJPlatformType?,
    versionProvider: Provider<String>,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_SOURCES,
) = addProvider(configurationName, versionProvider.map { createIntelliJPlatformSourcesDependency(type, it) })

internal fun DependencyHandlerScope.createIntelliJPlatformSourcesDependency(
    type: IntelliJPlatformType?,
    version: String,
) = when (type) {
    IntellijIdeaCommunity -> create(
        group = "com.jetbrains.intellij.idea",
        name = "ideaIC",
        version = version,
        classifier = "sources",
    )

    PyCharmCommunity -> create(
        group = "com.jetbrains.intellij.pycharm",
        name = "pycharmPC",
        version = version,
        classifier = "sources",
    )

    else -> throw IllegalArgumentException("Specified type '$type' is unknown. Supported values: $IntellijIdeaCommunity, $PyCharmCommunity")
}

fun DependencyHandlerScope.intellijPlatformSources(type: String, version: String) = intellijPlatformSources(IntelliJPlatformType.fromCode(type), version)
fun DependencyHandlerScope.intellijPlatformSources(type: String, version: Provider<String>) =
    intellijPlatformSources(IntelliJPlatformType.fromCode(type), version)

fun DependencyHandlerScope.intellijIdeaCommunitySources(version: String) = intellijPlatformSources(IntellijIdeaCommunity, version)
fun DependencyHandlerScope.intellijIdeaCommunitySources(version: Provider<String>) = intellijPlatformSources(IntellijIdeaCommunity, version)

fun DependencyHandlerScope.pycharmCommunitySources(version: String) = intellijPlatformSources(PyCharmCommunity, version)
fun DependencyHandlerScope.pycharmCommunitySources(version: Provider<String>) = intellijPlatformSources(PyCharmCommunity, version)
