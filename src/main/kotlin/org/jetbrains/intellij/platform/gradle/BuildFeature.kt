// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.Project
import org.gradle.api.provider.ProviderFactory
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_ID as prefix

/**
 * Switch to [org.gradle.api.configuration.BuildFeatures] when supporting Gradle 8.5+.
 */
enum class BuildFeature(private val defaultValue: Boolean) {
    NO_SEARCHABLE_OPTIONS_WARNING(true),
    PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING(true),
    SELF_UPDATE_CHECK(true),
    USE_CACHE_REDIRECTOR(true),
    ;

    fun getValue(providers: ProviderFactory) = providers.gradleProperty(toString())
        .map { it.toBoolean() }
        .orElse(defaultValue)

    override fun toString() = name
        .lowercase()
        .split('_')
        .joinToString(
            separator = "",
            transform = { it.replaceFirstChar(Char::uppercase) },
        )
        .replaceFirstChar(Char::lowercase)
        .let { "$prefix.buildFeature.$it" }
}

fun Project.isBuildFeatureEnabled(feature: BuildFeature) =
    feature
        .getValue(providers)
        .map { value ->
            value.also {
                when (value) {
                    true -> "Build feature is enabled: $feature"
                    false -> "Build feature is disabled: $feature"
                }
                    .also { Logger(BuildFeature::class.java).info(value.toString()) }
            }
        }
