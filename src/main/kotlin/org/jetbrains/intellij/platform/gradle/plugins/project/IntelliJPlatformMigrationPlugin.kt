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
private const val OLD_INTELLIJ_EXTENSION = "intellij"

@Suppress("unused")
abstract class IntelliJPlatformMigrationPlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: $PLUGIN_MIGRATION_ID")

        project.configureExtension<IntelliJExtension>(OLD_INTELLIJ_EXTENSION)

        log.error("""
        You are currently using the $PLUGIN_MIGRATION_ID which is dedicated only to help with the migration process from the Gradle IntelliJ Plugin 1.x to IntelliJ Platform Gradle Plugin 2.0.
        As soon as you'll handle all configuration problems, remember to remove this plugin from your `plugins {}` block.
        
        Note: we encourage to avoid configuring tasks, such as `patchPluginXml`, `publishPlugin`, `signPlugin`, etc. manually but use the `intellijPlatform {}` extension instead.
        
        See: $DOCS/intellij-platform-gradle-plugin-extension-plugin.xml
        Migration guide: $DOCS/tools-intellij-platform-gradle-plugin-migration.html
        """.trimIndent())
    }
}

// language=TEXT
interface IntelliJExtension {

    @Deprecated(
        message = "\nThe `intellij.pluginName` property is no longer available.\nUse: `intellijPlatform.pluginConfiguration.name` instead:\n\nintellijPlatform {\n    pluginConfiguration {\n        name = ...\n    }\n}\n\nSee: $DOCS/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginConfiguration\nMigration guide: $DOCS/tools-intellij-platform-gradle-plugin-migration.html\n",
        level = DeprecationLevel.ERROR,
    )
    val pluginName: Property<String>

    @Deprecated(
        message = "\nThe `intellij.version` property is no longer available.\nDefine the IntelliJ Platform dependency in `dependencies {}` block instead:\n\nrepositories {\n    mavenCentral()\n    intellijPlatform {\n        defaultRepositories()\n    }\n}\n\ndependencies {\n    intellijPlatform {\n        create(type, version)\n    }\n}\n\nSee: $DOCS/tools_intellij_platform_gradle_plugin_extension.md#intellijPlatform-pluginConfiguration-name\nMigration guide: $DOCS/tools-intellij-platform-gradle-plugin-migration.html\n",
        level = DeprecationLevel.ERROR,
    )
    val version: Property<String>

    @Deprecated(
        message = "\nThe `intellij.type` property is no longer available.\nDefine the IntelliJ Platform dependency in `dependencies {}` block instead:\n\nrepositories {\n    mavenCentral()\n    intellijPlatform {\n        defaultRepositories()\n    }\n}\n\ndependencies {\n    intellijPlatform {\n        create(type, version)\n    }\n}\n\nSee: $DOCS/tools-intellij-platform-gradle-plugin-dependencies-extension.html\nMigration guide: $DOCS/tools-intellij-platform-gradle-plugin-migration.html\n",
        level = DeprecationLevel.ERROR,
    )
    val type: Property<String>

    @Deprecated(
        message = "\nThe `intellij.plugins` property is no longer available.\nDefine dependencies on plugins or bundled plugins in `dependencies {}` block instead:\n\nrepositories {\n    mavenCentral()\n    intellijPlatform {\n        defaultRepositories()\n    }\n}\n\ndependencies {\n    intellijPlatform {\n        plugins(properties(\"platformPlugins\").map { it.split(',') })\n        bundledPlugins(properties(\"platformBundledPlugins\").map { it.split(',') })\n    }\n}\n\nNote that bundled plugins are now separated from plugins available in JetBrains Marketplace.\n\nSee: $DOCS/tools-intellij-platform-gradle-plugin-dependencies-extension.html\nMigration guide: $DOCS/tools-intellij-platform-gradle-plugin-migration.html\n",
        level = DeprecationLevel.ERROR,
    )
    val plugins: ListProperty<Any>
}
