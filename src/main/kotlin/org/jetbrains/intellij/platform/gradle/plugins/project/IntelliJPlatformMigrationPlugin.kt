// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Locations.DOCS
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_ID
import org.jetbrains.intellij.platform.gradle.plugins.configureExtension
import org.jetbrains.intellij.platform.gradle.utils.Logger

private const val PLUGIN_MIGRATION_ID = "$PLUGIN_ID.migration"

// language=TEXT
private object Messages {
    const val DEPRECATED_INTELLIJ_PLUGIN_NAME =
        "\nThe `intellij.pluginName` property is no longer available.\nUse: `intellijPlatform.pluginConfiguration.name` instead:\n\nintellijPlatform {\n    pluginConfiguration {\n        name = ...\n    }\n}\n\nSee: $DOCS/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginConfiguration\nMigration guide: $DOCS/tools-intellij-platform-gradle-plugin-migration.html"
    const val DEPRECATED_INTELLIJ_TYPE_VERSION =
        "\nThe `intellij.type` and `intellij.version` properties are no longer available.\nDefine the IntelliJ Platform dependency in `dependencies {}` block instead:\n\nrepositories {\n    mavenCentral()\n    intellijPlatform {\n        defaultRepositories()\n    }\n}\n\ndependencies {\n    intellijPlatform {\n        create(type, version)\n    }\n}\n\nSee: $DOCS/tools_intellij_platform_gradle_plugin_extension.md#intellijPlatform-pluginConfiguration-name\nMigration guide: $DOCS/tools-intellij-platform-gradle-plugin-migration.html"
    const val DEPRECATED_INTELLIJ_PLUGINS =
        "\nThe `intellij.plugins` property is no longer available.\nDefine dependencies on plugins or bundled plugins in `dependencies {}` block instead:\n\nrepositories {\n    mavenCentral()\n    intellijPlatform {\n        defaultRepositories()\n    }\n}\n\ndependencies {\n    intellijPlatform {\n        plugins(properties(\"platformPlugins\").map { it.split(',') })\n        bundledPlugins(properties(\"platformBundledPlugins\").map { it.split(',') })\n    }\n}\n\nNote that bundled plugins are now separated from plugins available in JetBrains Marketplace.\n\nSee: $DOCS/tools-intellij-platform-gradle-plugin-dependencies-extension.html\nMigration guide: $DOCS/tools-intellij-platform-gradle-plugin-migration.html\n"
    const val MIGRATION_PLUGIN_ENABLED =
        "You are currently using the $PLUGIN_MIGRATION_ID which is dedicated only to help with the migration process from the Gradle IntelliJ Plugin 1.x to IntelliJ Platform Gradle Plugin 2.0.\nAs soon as you'll handle all configuration problems, remember to remove this plugin from your `plugins {}` block.\n\nNote: We encourage avoiding configuring tasks directly, like: patchPluginXml { ... }, publishPlugin { ... }, signPlugin { ... }, ...\n      Use the `intellijPlatform { }` extension instead:\n      \nintellijPlatform {\n    ...\n    \n    pluginConfiguration { ... }\n    publishing { ... }\n    signing { ... }\n    ...\n}\n\nSee: $DOCS/intellij-platform-gradle-plugin-extension-plugin.xml\nMigration guide: $DOCS/tools-intellij-platform-gradle-plugin-migration.html"
}

@Suppress("unused")
abstract class IntelliJPlatformMigrationPlugin : Plugin<Project> {

    private val log = Logger(javaClass)
    override fun apply(project: Project) {
        log.info("Configuring plugin: $PLUGIN_MIGRATION_ID")
        log.error(Messages.MIGRATION_PLUGIN_ENABLED)

        project.configureExtension<IntelliJExtension>("intellij")
    }
}

interface IntelliJExtension {

    @Deprecated(Messages.DEPRECATED_INTELLIJ_PLUGIN_NAME, level = DeprecationLevel.ERROR)
    val pluginName: Property<String>

    @Deprecated(Messages.DEPRECATED_INTELLIJ_TYPE_VERSION, level = DeprecationLevel.ERROR)
    val version: Property<String>

    @Deprecated(Messages.DEPRECATED_INTELLIJ_TYPE_VERSION, level = DeprecationLevel.ERROR)
    val type: Property<String>

    @Deprecated(Messages.DEPRECATED_INTELLIJ_PLUGINS, level = DeprecationLevel.ERROR)
    val plugins: ListProperty<Any>

}
