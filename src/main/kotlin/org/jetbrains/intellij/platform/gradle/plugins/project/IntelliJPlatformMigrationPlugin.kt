// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("ConstPropertyName", "unused", "DeprecatedCallableAddReplaceWith")

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.register
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Plugin.ID
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.plugins.configureExtension
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.nio.file.Path
import javax.inject.Inject

private const val PLUGIN_MIGRATION_ID = "$ID.migration"

// language=TEXT
private object Docs {
    const val migration =
        "Migration guide: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-migration.html"
    const val extension =
        "IntelliJ Platform Extension: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html"
    const val dependencies =
        "IntelliJ Platform Extension: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html"
    const val repositories =
        "IntelliJ Platform Repositories Extension: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html"
}

// language=TEXT
private object Messages {
    object IntelliJ {
        const val plugins =
            "Define dependencies on plugins or bundled plugins in `dependencies {}` block instead:\n\nrepositories {\n    mavenCentral()\n    intellijPlatform {\n        defaultRepositories()\n    }\n}\n\ndependencies {\n    intellijPlatform {\n        plugins(providers.gradleProperty(\"platformPlugins\").map { it.split(',') })\n        bundledPlugins(providers.gradleProperty(\"platformBundledPlugins\").map { it.split(',') })\n    }\n}\n\nNote that bundled plugins are now separated from plugins available in JetBrains Marketplace.\n\n${Docs.extension}\n${Docs.migration}"

        const val localPath =
            "Define dependencies on local IDE instance in `dependencies {}` block instead:\n\nrepositories {\n    mavenCentral()\n    intellijPlatform {\n        defaultRepositories()\n    }\n}\n\ndependencies {\n    intellijPlatform {\n        local(...)\n    }\n}\n\n${Docs.dependencies}\n${Docs.migration}"

        const val localSourcesPath =
            "Providing `localSourcesPath` is no longer available.\n\n${Docs.extension}\n${Docs.migration}"

        const val typeVersion =
            "Define the IntelliJ Platform dependency in `dependencies {}` block instead:\n\nrepositories {\n    mavenCentral()\n    intellijPlatform {\n        defaultRepositories()\n    }\n}\n\ndependencies {\n    intellijPlatform {\n        create(type, version)\n    }\n}\n\n${Docs.dependencies}\n${Docs.migration}"

        const val pluginName =
            "Use: `intellijPlatform.pluginConfiguration.name` instead:\n\nintellijPlatform {\n    pluginConfiguration {\n        name = ...\n    }\n}\n\n${Docs.extension}#intellijPlatform-pluginConfiguration\n${Docs.migration}"

        const val sandboxDir =
            "Use the `intellijPlatform.sandboxContainer`.\n\n${Docs.extension}\n${Docs.migration}"

        const val downloadSources =
            "Downloading sources is managed with the Plugin DevKit plugin in version 2024.1+.\n\n${Docs.migration}"

        const val ideaDependency =
            "Access now the `ProductInfo` object using the `intellijPlatform.productInfo` property.\n\n${Docs.migration}"
    }

    object Tasks {
        const val downloadRobotServerPlugin =
            "The Robot Server Plugin integration is not yet available. Stay tuned!\n\n${Docs.migration}"

        const val runIdeForUiTests =
            "Use `testIdeUi` task.\n\n${Docs.migration}"
    }

    const val enabled =
        "You are currently using the $PLUGIN_MIGRATION_ID which is dedicated only to help with the migration process from the Gradle IntelliJ Plugin 1.x to IntelliJ Platform Gradle Plugin 2.0.\nAs soon as you'll handle all configuration problems, remember to remove this plugin from your `plugins {}` block and eventually rename the `intellij {}` extension to `intellijPlatform {}`.\n\nNote: We encourage avoiding configuring tasks directly, like: patchPluginXml { ... }, publishPlugin { ... }, signPlugin { ... }, ...\n      Use the `intellijPlatform { }` extension instead:\n      \nintellijPlatform {\n    ...\n    \n    pluginConfiguration { ... }\n    publishing { ... }\n    signing { ... }\n    ...\n}\n\n${Docs.extension}\n${Docs.migration}"

    const val sinceUntilBuild =
        "The `plugin.xml` file is now fully managed by the `intellijPlatform` extension`.\n\n${Docs.extension}#intellijPlatform-pluginConfiguration-ideaVersion\n${Docs.migration}"

    const val repositoryManagement =
        "Use the `repositories {}` block to manage repositories instead.\n\n${Docs.repositories}\n${Docs.migration}"

    const val verifyPlugin =
        "Task responsible for running the IntelliJ Plugin Verifier is now called `verifyPlugin`.\n\nUse `intellijPlatform.verifyPlugin` extension to configure it.\n\n${Docs.repositories}#intellijPlatform-verifyPlugin\n${Docs.migration}"
}

@Suppress("unused")
abstract class IntelliJPlatformMigrationPlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: $PLUGIN_MIGRATION_ID")
        log.error(Messages.enabled)

        project.plugins.apply(IntelliJPlatformPlugin::class)

        project.configureExtension<IntelliJExtension>(
            "intellij",
            project.configurations,
            project.providers,
            project.rootDir.toPath(),
        )

        with(project.tasks) {
            register<DownloadRobotServerPluginTask>("downloadRobotServerPlugin")
            register<RunIdeForUiTestsTask>("runIdeForUiTests")
            register<RunPluginVerifierTask>("runPluginVerifier")
        }
    }
}


abstract class IntelliJExtension @Inject constructor(
    configurations: ConfigurationContainer,
    providers: ProviderFactory,
    rootProjectDirectory: Path,
) : IntelliJPlatformExtension(configurations, providers, rootProjectDirectory) {

    @Deprecated(Messages.IntelliJ.plugins, level = DeprecationLevel.ERROR)
    abstract val plugins: ListProperty<Any>

    @Deprecated(Messages.IntelliJ.localPath, level = DeprecationLevel.ERROR)
    abstract val localPath: Property<String>

    @Deprecated(Messages.IntelliJ.localSourcesPath, level = DeprecationLevel.ERROR)
    abstract val localSourcesPath: Property<String>

    @Deprecated(Messages.IntelliJ.typeVersion, level = DeprecationLevel.ERROR)
    abstract val version: Property<String>

    @Deprecated(Messages.IntelliJ.typeVersion, level = DeprecationLevel.ERROR)
    abstract val type: Property<String>

    @Deprecated(Messages.IntelliJ.pluginName, level = DeprecationLevel.ERROR)
    abstract val pluginName: Property<String>

    @Deprecated(Messages.sinceUntilBuild, level = DeprecationLevel.ERROR)
    abstract val updateSinceUntilBuild: Property<Boolean>

    @Deprecated(Messages.sinceUntilBuild, level = DeprecationLevel.ERROR)
    abstract val sameSinceUntilBuild: Property<Boolean>

    @Deprecated(
        Messages.IntelliJ.sandboxDir,
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("sandboxContainer")
    )
    abstract val sandboxDir: Property<String>

    @Deprecated(Messages.repositoryManagement, level = DeprecationLevel.ERROR)
    abstract val intellijRepository: Property<String>

    @Suppress("IdentifierGrammar")
    @Deprecated(Messages.repositoryManagement, level = DeprecationLevel.ERROR)
    abstract val pluginsRepositories: Property<String>

    @Deprecated(Messages.repositoryManagement, level = DeprecationLevel.ERROR)
    abstract val jreRepository: Property<String>

    @Deprecated(Messages.IntelliJ.downloadSources, level = DeprecationLevel.ERROR)
    abstract val downloadSources: Property<Boolean>

    @Deprecated(
        Messages.IntelliJ.ideaDependency,
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("productInfo")
    )
    abstract val ideaDependency: Property<Any>
}

@DisableCachingByDefault
abstract class DownloadRobotServerPluginTask : DefaultTask() {

    @Deprecated(Messages.Tasks.downloadRobotServerPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val version: Property<String>
}

@DisableCachingByDefault
abstract class RunIdeForUiTestsTask : DefaultTask() {

    @Deprecated(Messages.Tasks.runIdeForUiTests, level = DeprecationLevel.ERROR)
    fun systemProperty(vararg arguments: Any) = Unit
}

@DisableCachingByDefault
abstract class RunPluginVerifierTask : DefaultTask() {

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val failureLevel: ListProperty<Any>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val productsReleasesFile: Property<Any>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val ideVersions: ListProperty<String>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val localPaths: ListProperty<Any>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val verifierVersion: Property<String>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val verifierPath: Property<String>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val freeArgs: ListProperty<String>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val distributionFile: Property<Any>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val verificationReportsDir: Property<String>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val verificationReportsFormats: ListProperty<Any>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val downloadDir: Property<String>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val downloadPath: Property<Any>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val jbrVersion: Property<String>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val jbrVariant: Property<String>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val jbrArch: Property<String>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val jreRepository: Property<String>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val runtimeDir: Property<String>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val resolvedRuntimeDir: Property<Any>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val externalPrefixes: ListProperty<String>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val teamCityOutputFormat: Property<Boolean>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val subsystemsToCheck: Property<String>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val ignoredProblems: Property<Any>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val ideDir: Property<Any>

    @Deprecated(Messages.verifyPlugin, level = DeprecationLevel.ERROR)
    @get:Internal
    abstract val offline: Property<Boolean>
}
