package org.jetbrains.intellij

import org.gradle.api.Project
import org.jetbrains.intellij.IntelliJPluginConstants.ID as prefix

enum class BuildFeature(private val defaultValue: Boolean) {
    CHECK_UPDATES(true),
    ;

     private fun getKey() = name
        .toLowerCase()
        .split('_')
        .joinToString("", transform = String::capitalize)
        .decapitalize()
        .let { "$prefix.buildFeature.$it" }

    internal fun getValue(project: Project): Boolean = project.findProperty(getKey())?.toString()?.toBoolean() ?: defaultValue

    override fun toString() = getKey()
}

fun Project.isBuildFeatureEnabled(feature: BuildFeature) =
    feature.getValue(this).apply {
        when (this) {
            true -> "Build feature is enabled: $feature"
            false -> "Build feature is disabled: $feature"
        }.also { info(logCategory(), it) }
    }
