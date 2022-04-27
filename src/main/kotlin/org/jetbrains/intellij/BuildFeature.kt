package org.jetbrains.intellij

import org.gradle.api.Project
import org.jetbrains.intellij.IntelliJPluginConstants.ID as prefix

enum class BuildFeature(private val defaultValue: Boolean) {
    CHECK_UPDATES(true),
    ;

    fun getValue(project: Project) = project.findProperty(toString())?.toString()?.toBoolean() ?: defaultValue

    override fun toString() = name
        .toLowerCase()
        .split('_')
        .joinToString("") { it.capitalize() }
        .decapitalize()
        .let { "$prefix.buildFeature.$it" }
}

fun Project.isBuildFeatureEnabled(feature: BuildFeature) =
    feature.getValue(this).apply {
        when (this) {
            true -> "Build feature is enabled: $feature"
            false -> "Build feature is disabled: $feature"
        }.also { info(logCategory(), it) }
    }
