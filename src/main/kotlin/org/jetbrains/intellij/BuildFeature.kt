package org.jetbrains.intellij

import org.gradle.api.Project
import org.jetbrains.intellij.IntelliJPluginConstants.ID as prefix

enum class BuildFeature(private val defaultValue: Boolean) {
    CHECK_UPDATES(true),
    ;

     fun getKey() = name
        .toLowerCase()
        .split('_')
        .joinToString("", transform = String::capitalize)
        .decapitalize()
        .let { "$prefix.buildFeature.$it" }

    fun getValue(project: Project) = project.findProperty(getKey())?.toString()?.toBoolean() ?: defaultValue

    override fun toString() = getKey()
}

fun Project.isBuildFeatureEnabled(feature: BuildFeature) =
    feature.getValue(this).apply {
        when (this) {
            true -> "Build feature is enabled: ${feature.getKey()}"
            false -> "Build feature is disabled: ${feature.getKey()}"
        }.also { info(logCategory(), it) }
    }
