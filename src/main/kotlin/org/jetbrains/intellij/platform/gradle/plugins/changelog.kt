// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.jetbrains.intellij.platform.gradle.tasks.PublishPluginTask
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider

private const val CHANGELOG_EXTENSION_NAME = "changelog"
private const val CHANGELOG_PATCH_TASK_NAME = "patchChangelog"

internal fun Project.setupChangelogConventions() {
    val pluginConfiguration = project.extensionProvider.map { it.pluginConfiguration }.get()
    val changelog = extensions.getByName(CHANGELOG_EXTENSION_NAME)

    changelog.listProperty("getGroups").convention(emptyList())
    changelog.stringProperty("getRepositoryUrl").convention(providers.gradleProperty("pluginRepositoryUrl"))
    changelog.stringProperty("getVersionPrefix").convention("")

    // Use reflection to avoid coupling the plugin runtime to a specific changelog-plugin classloader/version.
    pluginConfiguration.changeNotes.convention(
        pluginConfiguration.version.map { version ->
            changelog.renderChangeNotes(version)
        },
    )

    tasks.withType(PublishPluginTask::class.java).configureEach {
        dependsOn(CHANGELOG_PATCH_TASK_NAME)
    }
}

@Suppress("UNCHECKED_CAST")
private fun Any.listProperty(getterName: String) = javaClass.getMethod(getterName).invoke(this) as ListProperty<String>

@Suppress("UNCHECKED_CAST")
private fun Any.stringProperty(getterName: String) = javaClass.getMethod(getterName).invoke(this) as Property<String>

/**
 * Renders changelog item for the given version.
 *
 * ```
 * changeNotes = version.map { pluginVersion ->
 *     with(changelog) {
 *         renderItem(
 *             (getOrNull(pluginVersion) ?: getUnreleased())
 *                 .withHeader(false)
 *                 .withEmptySections(false),
 *             Changelog.OutputType.HTML,
 *         )
 *     }
 * }
 * ```
 */
private fun Any.renderChangeNotes(version: String): String {
    val changelogClass = javaClass
    val item = changelogClass.getMethod("getOrNull", String::class.java).invoke(this, version)
        ?: changelogClass.getMethod("getUnreleased").invoke(this)
    val itemWithoutHeader = item.javaClass.getMethod("withHeader", Boolean::class.javaPrimitiveType).invoke(item, false)
    val renderedItem = itemWithoutHeader.javaClass.getMethod("withEmptySections", Boolean::class.javaPrimitiveType)
        .invoke(itemWithoutHeader, false)
    val outputType = changelogClass
        .classLoader
        .loadClass("org.jetbrains.changelog.Changelog\$OutputType")
        .enumConstants
        .first { (it as Enum<*>).name == "HTML" }

    return changelogClass.methods
        .single { it.name == "renderItem" && it.parameterCount == 2 }
        .invoke(this, renderedItem, outputType) as String
}
