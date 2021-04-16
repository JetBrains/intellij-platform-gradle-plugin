package org.jetbrains.intellij

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.dependency.*

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
     * Please see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html">Plugin Compatibility</a> in SDK docs for more details.
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
    Object sandboxDirectory

    /**
     * Url of repository for downloading IDE distributions.
     */
    String intellijRepo = IntelliJPlugin.DEFAULT_INTELLIJ_REPO

    /**
     * Url of repository for downloading plugin dependencies.
     *
     * @deprecated Use closure syntax to configure multiple repositories
     */
    @Deprecated
    String pluginsRepo = IntelliJPlugin.DEFAULT_INTELLIJ_PLUGINS_REPO

    /**
     * Returns object to configure multiple repositories for downloading plugins.
     */
    PluginsRepoConfiguration pluginsRepo() {
        if (pluginsRepoConfiguration == null) {
            pluginsRepoConfiguration = new PluginsRepoConfigurationImpl(project)
        }
        return pluginsRepoConfiguration
    }

    /**
     * Configure multiple repositories for downloading plugins.
     */
    void pluginsRepo(Closure<?> block) {
        this.project.configure(pluginsRepo(), block)
    }

    /**
     * Configure multiple repositories for downloading plugins.
     */
    void pluginsRepo(Action<PluginsRepoConfiguration> block) {
        block.execute(pluginsRepo())
    }


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
    private PluginsRepoConfigurationImpl pluginsRepoConfiguration = null

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
        } else if (version.startsWith('GO-') || 'GO' == type) {
            return 'GO'
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
                || version.startsWith('PY-') || version.startsWith('PC-') || version.startsWith('GO-')) {
            return version.substring(3)
        }
        return version
    }

    String getBuildVersion() {
        return IdeVersion.createIdeVersion(getIdeaDependency().buildNumber).asStringWithoutProductCode()
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
            project.configurations.getByName(IntelliJPlugin.IDEA_PLUGINS_CONFIGURATION_NAME).resolve()
            pluginDependenciesConfigured = true
        }
        return pluginDependencies
    }

    def setIdeaDependency(IdeaDependency ideaDependency) {
        this.ideaDependency = ideaDependency
    }

    IdeaDependency getIdeaDependency() {
        if (ideaDependency == null) {
            Utils.debug(project, "IDE dependency is resolved", new Throwable())
            project.configurations.getByName(IntelliJPlugin.IDEA_CONFIGURATION_NAME).resolve()
            if (ideaDependency == null) {
                throw new BuildException("Cannot resolve ideaDependency", null)
            }
        }
        return ideaDependency
    }

    @NotNull
    List<PluginsRepository> getPluginsRepos() {
        if (pluginsRepoConfiguration == null) {
            //noinspection GrDeprecatedAPIUsage
            pluginsRepo().maven(this.pluginsRepo)
        }
        return pluginsRepoConfiguration.getRepositories()
    }

    interface PluginsRepoConfiguration {

        /**
         * Use default marketplace repository
         */
        void marketplace()

        /**
         * Use a Maven repository with plugin artifacts
         */
        void maven(@NotNull String url)

        /**
         * Use custom plugin repository. The URL should point to the `plugins.xml` or `updatePlugins.xml` file.
         */
        void custom(@NotNull String url)

        List<PluginsRepository> getRepositories()
    }

}
