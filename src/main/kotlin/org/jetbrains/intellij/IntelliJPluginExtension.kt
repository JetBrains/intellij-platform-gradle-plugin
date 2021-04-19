package org.jetbrains.intellij

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.util.ConfigureUtil
import org.jetbrains.intellij.dependency.PluginsRepoConfiguration

/**
 * Configuration options for the {@link org.jetbrains.intellij.IntelliJPlugin}.
 * TODO: Annotate props properly with @Input, @Optional, etc
 */
@Suppress("UnstableApiUsage")
abstract class IntelliJPluginExtension(objects: ObjectFactory) {

    /**
     * The list of bundled IDE plugins and plugins from the <a href="https://plugins.jetbrains.com/">JetBrains Plugin Repository</a>.
     * Accepts values of `String` or `Project`.
     */
    val plugins: ListProperty<Any> = objects.listProperty(Any::class.java)

    /**
     * The path to locally installed IDE distribution that should be used as a dependency.
     */
    val localPath: Property<String> = objects.property(String::class.java)

    /**
     * The path to local archive with IDE sources.
     */
    val localSourcesPath: Property<String> = objects.property(String::class.java)

    /**
     * The version of the IntelliJ Platform IDE that will be used to build the plugin.
     * <p/>
     * Please see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html">Plugin Compatibility</a> in SDK docs for more details.
     */
    val version: Property<String> = objects.property(String::class.java)

    /**
     * The type of IDE distribution (IC, IU, CL, PY, PC, RD or JPS).
     * <p/>
     * The type might be included as a prefix in {@link #version} value.
     */
    val type: Property<String> = objects.property(String::class.java)

    /**
     * The name of the target zip-archive and defines the name of plugin artifact.
     * By default: <code>${project.name}</code>
     */
    val pluginName: Property<String> = objects.property(String::class.java)

    /**
     * Patch plugin.xml with since and until build values inferred from IDE version.
     */
    val updateSinceUntilBuild: Property<Boolean> = objects.property(Boolean::class.java)

    /**
     * Patch plugin.xml with an until build value that is just an "open" since build.
     */
    val sameSinceUntilBuild: Property<Boolean> = objects.property(Boolean::class.java)

    /**
     * Instrument Java classes with nullability assertions and compile forms created by IntelliJ GUI Designer.
     */
    val instrumentCode: Property<Boolean> = objects.property(Boolean::class.java)

    /**
     * The path of sandbox directory that is used for running IDE with developing plugin.
     * By default: <code>${project.buildDir}/idea-sandbox</code>.
     */
    val sandboxDirectory: Property<String> = objects.property(String::class.java)

    /**
     * Url of repository for downloading IDE distributions.
     */
    val intellijRepo: Property<String> = objects.property(String::class.java)

    /**
     * Object to configure multiple repositories for downloading plugins.
     */
    @Nested
    // TODO: rename to pluginsRepositories
    val pluginsRepo: PluginsRepoConfiguration = objects.newInstance(PluginsRepoConfiguration::class.java)

    // TODO: make it as property and use convention?
    fun getPluginsRepos() = pluginsRepo.run {
        getRepositories().ifEmpty {
            marketplace()
            getRepositories()
        }
    }

    /**
     * Configure multiple repositories for downloading plugins.
     */
    fun pluginsRepo(block: Closure<Any>) {
        ConfigureUtil.configure(block, pluginsRepo)
    }

    /**
     * Configure multiple repositories for downloading plugins.
     */
    fun pluginsRepo(block: Action<PluginsRepoConfiguration>) {
        block.execute(pluginsRepo)
    }

    /**
     * Url of repository for downloading JetBrains Java Runtime.
     */
    val jreRepo: Property<String> = objects.property(String::class.java)

    /**
     * The absolute path to the local directory that should be used for storing IDE distributions.
     */
    val ideaDependencyCachePath: Property<String> = objects.property(String::class.java)

    /**
     * Download IntelliJ sources while configuring Gradle project.
     */
    val downloadSources: Property<Boolean> = objects.property(Boolean::class.java)

    /**
     * Turning it off disables configuring dependencies to intellij sdk jars automatically,
     * instead the intellij, intellijPlugin and intellijPlugins functions could be used for an explicit configuration
     */
    val configureDefaultDependencies: Property<Boolean> = objects.property(Boolean::class.java)

    /**
     * Configure extra dependency artifacts from intellij repo
     * The dependencies on them could be configured only explicitly using intellijExtra function in the dependencies block
     */
    val extraDependencies: ListProperty<String> = objects.listProperty(String::class.java)

//    private Project project
//    private IdeaDependency ideaDependency
//    private final Set<PluginDependency> pluginDependencies = new HashSet<>()
//    private boolean pluginDependenciesConfigured = false
//
//    def setExtensionProject(@NotNull Project project) {
//        this.project = project
//    }

    fun getVersionNumber() = version.orNull?.let { v ->
        val keys = listOf("JPS", "IU", "IC", "RD", "CL", "PY", "PC", "GO") // TODO: move somewhere
        val key = keys.find { v.startsWith("$it-") }
        when {
            key != null -> v.substring(key.length + 1)
            else -> v
        }
    }

    fun getVersionType(): String {
        val keys = listOf("JPS", "IU", "IC", "RD", "CL", "PY", "PC", "GO")
        val v = version.orNull ?: return "IC"
        val t = type.orNull

        return keys.find { v.startsWith("$it-") || it == t } ?: "IC"
    }

//    fun getBuildVersion() = IdeVersion.createIdeVersion(getIdeaDependency().buildNumber).asStringWithoutProductCode()

//    def addPluginDependency(@NotNull PluginDependency pluginDependency) {
//        pluginDependencies.add(pluginDependency)
//    }
//
//    Set<PluginDependency> getUnresolvedPluginDependencies() {
//        if (pluginDependenciesConfigured) {
//            return []
//        }
//        return pluginDependencies
//    }
//
//    Set<PluginDependency> getPluginDependencies() {
//        if (!pluginDependenciesConfigured) {
//            Utils.debug(project, "Plugin dependencies are resolved", new Throwable())
//            project.configurations.getByName(IntelliJPlugin.IDEA_PLUGINS_CONFIGURATION_NAME).resolve()
//            pluginDependenciesConfigured = true
//        }
//        return pluginDependencies
//    }
//
//    def setIdeaDependency(IdeaDependency ideaDependency) {
//        this.ideaDependency = ideaDependency
//    }
//
//    IdeaDependency getIdeaDependency() {
//        if (ideaDependency == null) {
//            Utils.debug(project, "IDE dependency is resolved", new Throwable())
//            project.configurations.getByName(IntelliJPlugin.IDEA_CONFIGURATION_NAME).resolve()
//            if (ideaDependency == null) {
//                throw new BuildException("Cannot resolve ideaDependency", null)
//            }
//        }
//        return ideaDependency
//    }
}
