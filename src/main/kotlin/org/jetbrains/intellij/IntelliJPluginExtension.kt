// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginsRepositoryConfiguration
import javax.inject.Inject

/**
 * Configuration options for the [org.jetbrains.intellij.IntelliJPlugin].
 */
abstract class IntelliJPluginExtension @Inject constructor(
    objectFactory: ObjectFactory,
) {
    companion object {
        private val versionTypeRegex = Regex("([A-Z]{2,3})-(.*)")
    }

    /**
     * The list of bundled IDE plugins and plugins from the [JetBrains Marketplace](https://plugins.jetbrains.com/)/
     * configured [pluginsRepositories].
     * It accepts values of `String` or `Project`.
     */
    @Input
    @Optional
    val plugins = objectFactory.listProperty<Any>()

    /**
     * The path to locally installed IDE distribution that should be used to build the plugin.
     */
    @Input
    @Optional
    val localPath = objectFactory.property<String>()

    /**
     * The path to local archive with IDE sources.
     */
    @Input
    @Optional
    val localSourcesPath = objectFactory.property<String>()

    /**
     * The version of the IntelliJ Platform IDE that will be used to build the plugin.
     *
     * Please see [Plugin Compatibility](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html)
     * topic in SDK docs for more details.
     */
    @Input
    val version = objectFactory.property<String>()

    /**
     * The type of IDE distribution (IC, IU, CL, PY, PC, RD, GO, or JPS).
     *
     * The type might be included as a prefix in [version] value.
     */
    @Input
    @Optional
    val type = objectFactory.property<String>()

    /**
     * The name of the target zip-archive and defines the name of plugin artifact.
     * By default, `${project.name}`.
     */
    @Input
    @Optional
    val pluginName = objectFactory.property<String>()

    /**
     * Patch `plugin.xml` with `since/until-build` values.
     */
    @Input
    @Optional
    val updateSinceUntilBuild = objectFactory.property<Boolean>()

    /**
     * Patch `plugin.xml` with an `until-build` value that is just an "open" `since-build`.
     */
    @Input
    @Optional
    val sameSinceUntilBuild = objectFactory.property<Boolean>()

    /**
     * Instrument Java classes with nullability assertions and compile forms created by IntelliJ GUI Designer.
     */
    @Input
    @Optional
    val instrumentCode = objectFactory.property<Boolean>()

    /**
     * The path of sandbox directory that is used for running IDE with developing plugin.
     * By default, `${project.buildDir}/idea-sandbox`.
     */
    @Input
    @Optional
    val sandboxDir = objectFactory.property<String>()

    /**
     * URL of repository for downloading IDE distributions.
     */
    @Input
    @Optional
    val intellijRepository = objectFactory.property<String>()

    /**
     * Object to configure multiple repositories for downloading plugins.
     */
    @Input
    @Optional
    @Nested
    val pluginsRepositories: PluginsRepositoryConfiguration = objectFactory.newInstance(PluginsRepositoryConfiguration::class.java)

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
    @Suppress("unused")
    fun pluginsRepositories(block: Action<PluginsRepositoryConfiguration>) {
        block.execute(pluginsRepositories)
    }

    /**
     * URL of repository for downloading JetBrains Runtime.
     */
    @Input
    @Optional
    val jreRepository = objectFactory.property<String>()

    /**
     * The absolute path to the local directory that should be used for storing IDE distributions.
     */
    val ideaDependencyCachePath = objectFactory.property<String>()

    /**
     * Download IntelliJ Platform sources.
     */
    @Input
    @Optional
    val downloadSources = objectFactory.property<Boolean>()

    /**
     * Turning it off disables configuring dependencies to Intellij SDK jars automatically,
     * instead, the intellij, intellijPlugin and intellijPlugins functions could be used for an explicit configuration.
     */
    @Input
    @Optional
    val configureDefaultDependencies = objectFactory.property<Boolean>()

    /**
     * Configure extra dependency artifacts from intellij repository.
     * The dependencies on them could be configured only explicitly using intellijExtra function in the dependencies block.
     */
    @Input
    @Optional
    val extraDependencies = objectFactory.listProperty<String>()

    @Internal
    val pluginDependencies = objectFactory.listProperty<PluginDependency>()

    @Internal
    @Deprecated("ideaDependency is moved to the SetupDependenciesTask.idea", ReplaceWith("setupDependencies.idea.get()"))
    val ideaDependency = objectFactory.property<IdeaDependency>()

    fun getVersionNumber(): String = version.get().run {
        versionTypeRegex.matchEntire(this)?.groupValues?.getOrNull(2) ?: this
    }

    fun getVersionType(): String = version.get().run {
        versionTypeRegex.matchEntire(this)?.groupValues?.getOrNull(1) ?: type.getOrElse("IC")
    }

    fun addPluginDependency(pluginDependency: PluginDependency) {
        pluginDependencies.add(pluginDependency)
    }

    fun getUnresolvedPluginDependencies(): Set<PluginDependency> {
        if (pluginDependenciesConfigured) {
            return emptySet()
        }
        return pluginDependencies.orNull?.toSet() ?: emptySet()
    }

    fun getPluginDependenciesList(project: Project): Set<PluginDependency> {
        if (!pluginDependenciesConfigured) {
            debug(project.logCategory(), "Plugin dependencies are resolved")
            project.configurations.getByName(IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME).resolve()
            pluginDependenciesConfigured = true
        }
        return pluginDependencies.orNull?.toSet() ?: emptySet()
    }

    @Suppress("DEPRECATION")
    @Deprecated("ideaDependency is moved to the SetupDependenciesTask.idea", ReplaceWith("setupDependencies.idea.get()"))
    fun getIdeaDependency(@Suppress("UNUSED_PARAMETER") project: Project): IdeaDependency = ideaDependency.get()
}

