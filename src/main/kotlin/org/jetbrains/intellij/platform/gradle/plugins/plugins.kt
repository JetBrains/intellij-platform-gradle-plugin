// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.create

internal inline fun <reified T : Any> Any.configureExtension(name: String, vararg constructionArguments: Any, noinline configuration: T.() -> Unit = {}) {
    with((this as ExtensionAware).extensions) {
        val extension = findByName(name) as? T ?: create<T>(name, *constructionArguments)
        extension.configuration()
    }
}

// TODO: remove inline
internal inline fun ConfigurationContainer.create(name: String, description: String, noinline configuration: Configuration.() -> Unit = {}) =
    maybeCreate(name).apply {
        isVisible = false
        isCanBeConsumed = false
        isCanBeResolved = true

        this.description = description
        configuration()
    }
