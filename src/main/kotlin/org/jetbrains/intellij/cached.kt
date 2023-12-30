// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.property

// https://github.com/gradle/gradle/issues/25550
internal inline fun <reified T : Any> Provider<T?>.cached(project: Project): Provider<T> =
    project
        .objects
        .property<T>()
        .value(this)
        .apply {
            disallowChanges()
            finalizeValueOnRead()
        }
