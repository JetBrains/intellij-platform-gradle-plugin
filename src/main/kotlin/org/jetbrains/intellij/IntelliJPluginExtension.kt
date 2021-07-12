package org.jetbrains.intellij

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.tooling.BuildException
import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginsRepositoryConfiguration
import javax.inject.Inject

/**
 * Configuration options for the {@link org.jetbrains.intellij.IntelliJPlugin}.
 * TODO: Annotate props properly with @Input, @Optional, etc
 */
@Suppress("UnstableApiUsage")
abstract class IntelliJPluginExtension @Inject constructor(
    objectFactory: ObjectFactory,
) {

    /**
     * The list of bundled IDE plugins and plugins from the <a href="https://plugins.jetbrains.com/">JetBrains Plugin Repository</a>.
     * Accepts values of `String` or `Project`.
     */
    @Input
    @Optional
    val plugins: ListProperty<Any> = objectFactory.listProperty(Any::class.java)

    /**
     * The path to locally installed IDE distribution that should be used as a dependency.
     */
    @Input
    @Optional
    val localPath: Property<String> = objectFactory.property(String::class.java)

    /**
     * The path to local archive with IDE sources.
     */
    @Input
    @Optional
    val localSourcesPath: Property<String> = objectFactory.property(String::class.java)

    /**
     * The version of the IntelliJ Platform IDE that will be used to build the plugin.
     * <p/>
     * Please see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html">Plugin Compatibility</a> in SDK docs for more details.
     */
    @Input
    @Optional
    val version: Property<String> = objectFactory.property(String::class.java)

    /**
     * The type of IDE distribution (IC, IU, CL, PY, PC, RD or JPS).
     * <p/>
     * The type might be included as a prefix in {@link #version} value.
     */
    @Input
    @Optional
    val type: Property<String> = objectFactory.property(String::class.java)

    /**
     * The name of the target zip-archive and defines the name of plugin artifact.
     * By default: <code>${project.name}</code>
     */
    @Input
    @Optional
    val pluginName: Property<String> = objectFactory.property(String::class.java)

    /**
     * Patch plugin.xml with since and until build values inferred from IDE version.
     */
    @Input
    @Optional
    val updateSinceUntilBuild: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * Patch plugin.xml with an until build value that is just an "open" since build.
     */
    @Input
    @Optional
    val sameSinceUntilBuild: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * Instrument Java classes with nullability assertions and compile forms created by IntelliJ GUI Designer.
     */
    @Input
    @Optional
    val instrumentCode: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * The path of sandbox directory that is used for running IDE with developing plugin.
     * By default: <code>${project.buildDir}/idea-sandbox</code>.
     */
    @Input
    @Optional
    val sandboxDir: Property<String> = objectFactory.property(String::class.java)

    /**
     * Url of repository for downloading IDE distributions.
     */
    @Input
    @Optional
    val intellijRepository: Property<String> = objectFactory.property(String::class.java)

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
     * Url of repository for downloading JetBrains Java Runtime.
     */
    @Input
    @Optional
    val jreRepository: Property<String> = objectFactory.property(String::class.java)

    /**
     * The absolute path to the local directory that should be used for storing IDE distributions.
     */
    val ideaDependencyCachePath: Property<String> = objectFactory.property(String::class.java)

    /**
     * Download IntelliJ sources while configuring Gradle project.
     */
    @Input
    @Optional
    val downloadSources: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * Turning it off disables configuring dependencies to intellij sdk jars automatically,
     * instead the intellij, intellijPlugin and intellijPlugins functions could be used for an explicit configuration
     */
    @Input
    @Optional
    val configureDefaultDependencies: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * Configure extra dependency artifacts from intellij repository
     * The dependencies on them could be configured only explicitly using intellijExtra function in the dependencies block
     */
    @Input
    @Optional
    val extraDependencies: ListProperty<String> = objectFactory.listProperty(String::class.java)

    @Internal
    val pluginDependencies: ListProperty<PluginDependency> = objectFactory.listProperty(PluginDependency::class.java)

    @Internal
    val ideaDependency: Property<IdeaDependency> = objectFactory.property(IdeaDependency::class.java)

    private val keys = listOf("JPS", "IU", "IC", "RD", "CL", "PY", "PC", "GO")

    fun getVersionNumber() = version.orNull?.let { v ->
        val key = keys.find { v.startsWith("$it-") }
        when {
            key != null -> v.substring(key.length + 1)
            else -> v
        }
    }

    fun getVersionType(): String {
        val v = version.orNull ?: return "IC"
        return keys.find { v.startsWith("$it-") } ?: type.orNull.takeIf { keys.contains(it) } ?: "IC"
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

    fun getIdeaDependency(project: Project): IdeaDependency {
        if (ideaDependency.orNull == null) {
            debug(project.logCategory(), "IDE dependency is resolved", Throwable())
            project.configurations.getByName(IntelliJPluginConstants.IDEA_CONFIGURATION_NAME).resolve()
            if (ideaDependency.orNull == null) {
                throw BuildException("Cannot resolve ideaDependency", null)
            }
        }
        return ideaDependency.get()
    }
}

