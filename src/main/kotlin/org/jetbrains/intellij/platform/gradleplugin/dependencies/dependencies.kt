// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.dependencies

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType

internal typealias Action = (Dependency.() -> Unit)

internal fun DependencyHandler.create(
    type: IntelliJPlatformType,
    version: String,
    action: Action = {},
) = create(
    group = type.groupId,
    name = type.artifactId,
    version = version,
).apply {
    action(this)
}
