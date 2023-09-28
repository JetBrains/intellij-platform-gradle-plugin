// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused")

package org.jetbrains.intellij.platform.gradleplugin.dependencies

import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations

fun DependencyHandlerScope.intellijPlatformBundledPlugin(
    id: String,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
) = create(
    group = "",
    name = id,
    version = "version",
).let { dependency ->
    add(configurationName, dependency)
}
