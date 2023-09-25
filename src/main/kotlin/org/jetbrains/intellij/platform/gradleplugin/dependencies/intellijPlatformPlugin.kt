// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused")

package org.jetbrains.intellij.platform.gradleplugin.dependencies

import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations

fun DependencyHandlerScope.intellijPlatformPlugin(
    id: String,
    version: String,
    channel: String = "",
    configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
) = create(
    group = when {
        channel.isBlank() -> "com.jetbrains.plugins"
        else -> "$channel.com.jetbrains.plugins"
    },
    name = id,
    version = version,
).let { dependency ->
    add(configurationName, dependency)
}
