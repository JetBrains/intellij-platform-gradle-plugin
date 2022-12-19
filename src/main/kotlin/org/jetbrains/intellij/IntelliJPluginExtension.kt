// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.newInstance
import org.jetbrains.intellij.IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_INTELLIJ_COMMUNITY
import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginsRepositoryConfiguration
import org.jetbrains.intellij.utils.DependenciesDownloader
import javax.inject.Inject

/**
 * Configuration options for the [org.jetbrains.intellij.IntelliJPlugin].
 */
abstract class IntelliJPluginExtension @Inject constructor(
    objectFactory: ObjectFactory,
    dependenciesDownloader: DependenciesDownloader,
) {
    companion object {
        private val versionTypeRegex = Regex("([A-Z]{2,3})-(.*)")
    }

    /**
     * The list of bundled IDE plugins and plugins from the [JetBrains Marketplace](https://plugins.jetbrains.com)
     * or configured [pluginsRepositories].
     *
     * Please see [Plugin Dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html) for more details.
     *
     * Notes:
     * - For plugins from JetBrains Plugin Repository, use format `pluginId:version`.
     * - For bundled plugins, version should be omitted: e.g. `org.intellij.groovy`.
     * - For subprojects, use project reference `project(':subproject')`.
     * - If you need to refer plugin's classes from your project, you also have to define a dependency in your `plugin.xml` file.
     *
     * **Acceptable Values:**
     * - `org.plugin.id:version[@channel]` format, `String` type:
     *     - `org.intellij.plugins.markdown:8.5.0`
     *     - `org.intellij.scala:2017.2.638@nightly`
     * - `bundledPluginName` format, `String` type:
     *     - `android`
     *     - `Groovy`
     * - `project(...)` format, `Project` type:
     *     - `project(":projectName")`
     *     - `project(":plugin-subproject")`
     */
    abstract val plugins: ListProperty<Any>

    /**
     * The path to the locally installed IDE distribution that should be used to build the plugin.
     *
     * Acceptable values:
     * - `C:\Users\user\AppData\Local\JetBrains\Toolbox\apps\IDEA-U\ch-0\211.7142.45`
     * - `/Applications/Android Studio 4.2 Preview.app/Contents`
     * - `/home/user/idea-IC-181.4445.78`
     *
     * Warning: [version] and [localPath] must not be specified at the same time.
     */
    abstract val localPath: Property<String>

    /**
     * The path to local archive with IDE sources.
     *
     * Default value: `null`
     */
    abstract val localSourcesPath: Property<String>

    /**
     * Required.
     * The version of the IntelliJ Platform IDE that will be used to build the plugin.
     * Please see [Plugin Compatibility](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html) topic in SDK docs for more details.
     *
     * Acceptable values:
     * - version number: `2022.1.1` or `IC-2022.1.1`
     * - build number: `221.5080.210` or `IC-221.5080.210`
     * - snapshot: `221-EAP-SNAPSHOT` or `LATEST-EAP-SNAPSHOT`
     *
     * All available JetBrains IDEs versions can be found at [IntelliJ Artifacts](https://plugins.jetbrains.com/docs/intellij/intellij-artifacts.html) page.
     */
    abstract val version: Property<String>

    /**
     * The type of the IntelliJ-based IDE distribution.
     * The type may also be specified as a prefix of the value for the [version] property.
     *
     * Default value: `IC`
     *
     * Acceptable values:
     * - `AI` - Android Studio
     * - `IC` - IntelliJ IDEA Community Edition
     * - `IU` - IntelliJ IDEA Ultimate Edition
     * - `CL` - CLion
     * - `PY` - PyCharm Professional Edition
     * - `PC` - PyCharm Community Edition
     * - `PS` - PhpStorm
     * - `RD` - Rider
     * - `GO` - GoLand
     * - `GW` - Gateway
     */
    abstract val type: Property<String>

    /**
     * The name of the generated ZIP archive distribution, e.g. `/build/distributions/PluginName-1.0.0.zip`.
     *
     * Default value: `${project.name}`
     */
    abstract val pluginName: Property<String>

    /**
     * Defines if the `plugin.xml` should be patched with the values of [org.jetbrains.intellij.tasks.PatchPluginXmlTask.sinceBuild]
     * and [org.jetbrains.intellij.tasks.PatchPluginXmlTask.untilBuild] properties.
     *
     * Default value: `true`
     */
    abstract val updateSinceUntilBuild: Property<Boolean>

    /**
     * Patches `plugin.xml` with the `patchPluginXml.untilBuild` with the value
     * of [org.jetbrains.intellij.tasks.PatchPluginXmlTask.sinceBuild] used with `*` wildcard, like `sinceBuild.*`, e.g., `220.*`.
     *
     * Notes:
     * - Useful for building plugins against EAP IDE builds.
     * - If [org.jetbrains.intellij.tasks.PatchPluginXmlTask.sinceBuild] has a value set, then [sameSinceUntilBuild] is ignored.
     *
     * Default value: `false`
     */
    abstract val sameSinceUntilBuild: Property<Boolean>

    /**
     * Instrument Java classes with nullability assertions and compile forms created by IntelliJ GUI Designer.
     *
     * Default value: `true`
     */
    abstract val instrumentCode: Property<Boolean>

    /**
     * The path of sandbox directory that is used for running IDE with developed plugin.
     *
     * Default value: `${project.buildDir}/idea-sandbox`
     */
    abstract val sandboxDir: Property<String>

    /**
     * The IntelliJ-based IDE distributions repository URL.
     *
     * Default value: `https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository`
     */
    abstract val intellijRepository: Property<String>

    /**
     * Configures repositories for downloading plugin dependencies.
     *
     * Default value: `pluginsRepositories { marketplace() }`
     *
     * Acceptable values:
     * - `marketplace()` - use Maven repository with plugins listed in the JetBrains Marketplace
     * - `maven(repositoryUrl)` - use custom Maven repository with plugins
     * - `maven { repositoryUrl }` - use custom Maven repository with plugins where you can configure additional parameters (credentials, authentication and etc.)
     * - `custom(pluginsXmlUrl)` - use custom plugin repository
     */
    val pluginsRepositories = objectFactory.newInstance<PluginsRepositoryConfiguration>(dependenciesDownloader)

    private var pluginDependenciesConfigured = false

    fun getPluginsRepositories() = pluginsRepositories.run {
        getRepositories().ifEmpty {
            marketplace()
            getRepositories()
        }
    }

    /**
     * Configure multiple repositories for downloading plugins.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun pluginsRepositories(block: Action<PluginsRepositoryConfiguration>) {
        block.execute(pluginsRepositories)
    }

    /**
     * URL of repository for downloading JetBrains Runtime.
     *
     * Default value: `null`
     */
    abstract val jreRepository: Property<String>

    /**
     * Path to the directory where IntelliJ IDEA dependency cache is stored.
     * If not set, the dependency will be extracted next to the downloaded ZIP archive in Gradle cache directory.
     *
     * Default value: `null`
     */
    abstract val ideaDependencyCachePath: Property<String>

    /**
     * Download IntelliJ Platform sources.
     * Enabled by default if not on `CI` environment.
     *
     * Default value: `!System.getenv().containsKey("CI")`
     */
    abstract val downloadSources: Property<Boolean>

    /**
     * If enabled, automatically configures the default IntelliJ Platform dependencies in the current project.
     * Otherwise, the [intellij], [intellijPlugin], and [intellijPlugins] functions could be used for an explicit configuration.
     *
     * Default value: `true`
     */
    abstract val configureDefaultDependencies: Property<Boolean>

    /**
     * Configure extra dependency artifacts from the IntelliJ repository.
     * The dependencies on them could be configured only explicitly using the [intellijExtra] function in the `dependencies` block.
     */
    abstract val extraDependencies: ListProperty<String>

    abstract val pluginDependencies: ListProperty<PluginDependency>

    @get:Deprecated("ideaDependency is moved to the SetupDependenciesTask.idea", ReplaceWith("setupDependencies.idea.get()"))
    abstract val ideaDependency: Property<IdeaDependency>

    fun getVersionNumber() = version.map {
        versionTypeRegex.matchEntire(it)?.groupValues?.getOrNull(2) ?: it
    }

    fun getVersionType() = version.map {
        versionTypeRegex.matchEntire(it)?.groupValues?.getOrNull(1)
            ?: type.getOrElse(PLATFORM_TYPE_INTELLIJ_COMMUNITY)
    }

    fun addPluginDependency(pluginDependency: PluginDependency) {
        pluginDependencies.add(pluginDependency)
    }

    fun getUnresolvedPluginDependencies(): Set<PluginDependency> {
        if (pluginDependenciesConfigured) {
            return emptySet()
        }
        return pluginDependencies.orNull?.toSet().orEmpty()
    }

    fun getPluginDependenciesList(project: Project): Set<PluginDependency> {
        if (!pluginDependenciesConfigured) {
            debug(project.logCategory(), "Plugin dependencies are resolved")
            project.configurations.getByName(IDEA_PLUGINS_CONFIGURATION_NAME).resolve()
            pluginDependenciesConfigured = true
        }
        return pluginDependencies.orNull?.toSet().orEmpty()
    }

    @Suppress("DEPRECATION")
    @Deprecated("ideaDependency is moved to the SetupDependenciesTask.idea", ReplaceWith("setupDependencies.idea.get()"))
    fun getIdeaDependency(@Suppress("UNUSED_PARAMETER") project: Project): IdeaDependency = ideaDependency.get()
}
