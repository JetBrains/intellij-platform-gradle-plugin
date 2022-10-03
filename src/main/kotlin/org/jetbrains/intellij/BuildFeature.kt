// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import org.gradle.api.Project
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_ID as prefix

enum class BuildFeature(private val defaultValue: Boolean) {
    NO_SEARCHABLE_OPTIONS_WARNING(true),
    PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING(true),
    SELF_UPDATE_CHECK(true),
    ;

    fun getValue(project: Project) = project.findProperty(toString())?.toString()?.toBoolean() ?: defaultValue

    override fun toString() = name
        .toLowerCase()
        .split('_')
        .joinToString("") { it.capitalize() }
        .decapitalize()
        .let { "$prefix.buildFeature.$it" }
}

internal fun Project.isBuildFeatureEnabled(feature: BuildFeature) =
    feature.getValue(this).apply {
        when (this) {
            true -> "Build feature is enabled: $feature"
            false -> "Build feature is disabled: $feature"
        }.also { info(logCategory(), it) }
    }
