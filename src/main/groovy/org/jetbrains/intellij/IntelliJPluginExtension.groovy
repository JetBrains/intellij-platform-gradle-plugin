package org.jetbrains.intellij

import org.gradle.api.Project
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.dependency.PluginDependency

/**
 * Configuration options for the {@link org.jetbrains.intellij.IntelliJPlugin}.
 */
class IntelliJPluginExtension {
    /**
     * The list of bundled IDE plugins and plugins from the <a href="https://plugins.jetbrains.com/">JetBrains Plugin Repository</a>.
     */
    Object[] plugins = []

    /**
     * The path to locally installed IDE distribution that should be used as a dependency.
     */
    String localPath

    /**
     * The path to local archive with IDE sources.
     */
    String localSourcesPath

    /**
     * The version of the IntelliJ Platform IDE that will be used to build the plugin.
     * <p/>
     * Please see <a href="https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/plugin_compatibility.html">Plugin Compatibility</a> in SDK docs for more details.
     */
    String version

    /**
     * The type of IDE distribution (IC, IU, CL, PY, PC, RD or JPS).
     * <p/>
     * The type might be included as a prefix in {@link #version} value.
     */
    String type = 'IC'

    /**
     * The name of the target zip-archive and defines the name of plugin artifact.
     * By default: <code>${project.name}</code>
     */
    String pluginName

    /**
     * Patch plugin.xml with since and until build values inferred from IDE version.
     */
    boolean updateSinceUntilBuild = true

    /**
     * Patch plugin.xml with an until build value that is just an "open" since build.
     */
    boolean sameSinceUntilBuild = false

    /**
     * Instrument Java classes with nullability assertions and compile forms created by IntelliJ GUI Designer.
     */
    boolean instrumentCode = true

    /**
     * The absolute path to the locally installed JetBrains IDE, which is used for running.
     * <p/>
     * @deprecated use `ideDirectory` option in `runIde` and `buildSearchableOptions` task instead.
     */
    @Deprecated
    String alternativeIdePath

    /**
     * The path of sandbox directory that is used for running IDE with developing plugin.
     * By default: <code>${project.buildDir}/idea-sandbox</code>.
     */
    String sandboxDirectory

    /**
     * Url of repository for downloading IDE distributions.
     */
    String intellijRepo = IntelliJPlugin.DEFAULT_INTELLIJ_REPO

    /**
     * Url of repository for downloading plugin dependencies.
     */
    String pluginsRepo = IntelliJPlugin.DEFAULT_INTELLIJ_PLUGINS_REPO

    /**
     * Url of repository for downloading JetBrains Java Runtime.
     */
    String jreRepo

    /**
     * The absolute path to the local directory that should be used for storing IDE distributions.
     */
    String ideaDependencyCachePath

    /**
     * Download IntelliJ sources while configuring Gradle project.
     */
    boolean downloadSources = !System.getenv().containsKey('CI')

    /**
     * Turning it off disables configuring dependencies to intellij sdk jars automatically,
     * instead the intellij, intellijPlugin and intellijPlugins functions could be used for an explicit configuration
     */
    boolean configureDefaultDependencies = true

    /**
     * configure extra dependency artifacts from intellij repo
     *  the dependencies on them could be configured only explicitly using intellijExtra function in the dependencies block
     */
    Object[] extraDependencies = []

    private Project project
    private IdeaDependency ideaDependency
    private final Set<PluginDependency> pluginDependencies = new HashSet<>()
    private boolean pluginDependenciesConfigured = false

    def setExtensionProject(@NotNull Project project) {
        this.project = project
    }

    String getType() {
        if (version == null) {
            return 'IC'
        }
        if (version.startsWith('IU-') || 'IU' == type) {
            return 'IU'
        } else if (version.startsWith('JPS-') || 'JPS' == type) {
            return "JPS"
        } else if (version.startsWith('CL-') || 'CL' == type) {
            return 'CL'
        } else if (version.startsWith('PY-') || 'PY' == type) {
            return 'PY'
        } else if (version.startsWith('PC-') || 'PC' == type) {
            return 'PC'
        } else if (version.startsWith('RD-') || 'RD' == type) {
            return 'RD'
        } else {
            return 'IC'
        }
    }

    String getVersion() {
        if (version == null) {
            return null
        }
        if (version.startsWith('JPS-')) {
            return version.substring(4)
        }
        if (version.startsWith('IU-') || version.startsWith('IC-') ||
                version.startsWith('RD-') || version.startsWith('CL-')
                || version.startsWith('PY-') || version.startsWith('PC-')) {
            return version.substring(3)
        }
        return version
    }

    def addPluginDependency(@NotNull PluginDependency pluginDependency) {
        pluginDependencies.add(pluginDependency)
    }

    Set<PluginDependency> getUnresolvedPluginDependencies() {
        if (pluginDependenciesConfigured) {
            return []
        }
        return pluginDependencies
    }

    Set<PluginDependency> getPluginDependencies() {
        if (!pluginDependenciesConfigured) {
            Utils.debug(project, "Plugin dependencies are resolved", new Throwable())
            pluginDependenciesConfigured = true
            project.configurations.getByName(IntelliJPlugin.IDEA_PLUGINS_CONFIGURATION_NAME).resolve()
        }
        return pluginDependencies
    }

    def setIdeaDependency(IdeaDependency ideaDependency) {
        this.ideaDependency = ideaDependency
    }

    def getIdeaDependency() {
        if (ideaDependency == null) {
            Utils.debug(project, "IDE dependency is resolved", new Throwable())
            project.configurations.getByName(IntelliJPlugin.IDEA_CONFIGURATION_NAME).resolve()
            if (ideaDependency == null) {
                throw new BuildException("Cannot resolve ideaDependency", null)
            }
        }
        return ideaDependency
    }
}
