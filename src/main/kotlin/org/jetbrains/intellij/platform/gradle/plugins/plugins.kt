// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins

import org.gradle.api.Task
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.api.provider.ListProperty
import org.gradle.kotlin.dsl.create
import org.gradle.util.GradleVersion
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.utils.Logger

internal inline fun <reified T : Any> Any.configureExtension(name: String, vararg constructionArguments: Any, noinline configuration: T.() -> Unit = {}) =
    with((this as ExtensionAware).extensions) {
        (findByName(name) as? T ?: create<T>(name, *constructionArguments)).apply(configuration)
    }

/**
 * @throws PluginInstantiationException
 */
@Throws(PluginInstantiationException::class)
internal fun checkGradleVersion() {
    if (GradleVersion.current() < Constraints.MINIMAL_GRADLE_VERSION) {
        throw PluginInstantiationException("${Plugin.NAME} requires Gradle ${Constraints.MINIMAL_GRADLE_VERSION} and higher")
    }
}

/**
 * Enables Compose Hot Reload compiler options for the current task when it exposes Kotlin compiler options.
 */
internal fun Task.enableComposeHotReloadCompilerOptions(log: Logger) = runCatching {
    fun Any.invokeNoArgsMethod(name: String) = javaClass.methods
        .firstOrNull { it.name == name && it.parameterCount == 0 }
        ?.invoke(this)

    @Suppress("UNCHECKED_CAST")
    val freeCompilerArgs = invokeNoArgsMethod("getCompilerOptions")
        ?.invokeNoArgsMethod("getFreeCompilerArgs") as? ListProperty<String>
        ?: return@runCatching

    log.info("Enabling Compose Hot Reload compiler options for '$path'")
    freeCompilerArgs.addAll(
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:generateFunctionKeyMetaAnnotations=true",
    )
}.onFailure {
    log.warn("Unable to enable Compose Hot Reload compiler options for '$path'", it)
}
