// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import com.jetbrains.plugin.structure.intellij.plugin.module.IdeModule
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XML
import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.initialization.resolve.RulesMode
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes.ArtifactType
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Dependencies
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.Constants.Locations.GITHUB_REPOSITORY
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.providers.*
import org.jetbrains.intellij.platform.gradle.resolvers.path.resolveJavaRuntimeDirectory
import org.jetbrains.intellij.platform.gradle.resolvers.path.resolveJavaRuntimeExecutable
import org.jetbrains.intellij.platform.gradle.services.*
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.utils.*
import org.jetbrains.intellij.platform.gradle.utils.IntelliJPlatformCacheResolver.Parameters
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import java.io.File
import java.io.FileReader
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.*

/**
 * Helper class for managing dependencies on the IntelliJ Platform in Gradle projects.
 *
 * @param configurations The Gradle [ConfigurationContainer] to manage configurations.
 * @param dependencyFactory The Gradle [DependencyHandler] to manage dependencies.
 * @param layout The Gradle [ProjectLayout] to manage layout providers.
 * @param objects The Gradle [ObjectFactory] used for creating objects.
 * @param providers The Gradle [ProviderFactory] used for creating providers.
 * @param projectPath The path (name) of the project.
 * @param gradle The Gradle [Gradle] instance.
 * @param rootProjectDirectory The root directory of the Gradle project.
 * @param metadataRulesModeProvider The [RulesMode] provider.
 */
class IntelliJPlatformDependenciesHelper(
    private val configurations: ConfigurationContainer,
    private val dependencyFactory: DependencyFactory,
    private val layout: ProjectLayout,
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
    private val projectPath: String,
    private val gradle: Gradle,
    private val rootProjectDirectory: Path,
    private val extensionProvider: Provider<IntelliJPlatformExtension>,
    private val metadataRulesModeProvider: Provider<RulesMode>,
) {

    private val log = Logger(javaClass)
    private val pluginManager by lazy { IdePluginManager.createManager() }
    private val pluginRepository by lazy { PluginRepositoryFactory.create(Locations.JETBRAINS_MARKETPLACE) }
    internal val requestedIntelliJPlatforms by lazy {
        gradle.registerClassLoaderScopedBuildService(RequestedIntelliJPlatformsService::class, projectPath) {
            parameters.useInstaller = true
        }.get()
    }
    private val extractorServiceProvider by lazy {
        gradle.registerClassLoaderScopedBuildService(ExtractorService::class)
    }

    /**
     * Key is an Ivy module XML filename.
     * Value is either:
     * 1. The Ivy Module, representing the contents of the XML file.
     *    In the case if we didn't have a path for the module.
     * 2. Otherwise, it is a pair of:
     *    - The Ivy Module, representing the contents of an XML file.
     *    - The path from which this module was created.
     *
     * Since we store Ivy XML files in the same directory, but create them from completely unrelated paths.
     * This structure is being used to validate that we don't try to create duplicate XML files with the same name from
     * different paths (which would either a bug in this plugin, or duplicated dependency on the file system).
     *
     * [ConcurrentHashMap] has been used because Gradle is multithreaded.
     * It won't provide absolute thread safety, but is good enough for the task, considering what this is for.
     *
     * @see writeIvyModule
     */
    private val writtenIvyModules = ConcurrentHashMap<String, Any>()

    private companion object {
        val IVY_MODULE_WRITE_LOCK = ReentrantLock()
    }

    /**
     * A thread-safe map that holds the paths associated with requested IntelliJ platform configurations.
     */
    private val requestedIntelliJPlatformPaths = ConcurrentHashMap<String, Provider<Path>>()

    /**
     * Helper function for accessing [ProviderFactory.provider] without exposing the whole [ProviderFactory].
     */
    internal inline fun <reified T : Any> provider(crossinline value: () -> T) = providers.provider {
        value()
    }

    /**
     * Creates a cached provider that evaluates and stores the value lazily.
     * The value is computed only once and reused for subsequent accesses.
     *
     * @receiver The provider to be cached
     * @return A cached provider wrapping the original provider
     */
    internal inline fun <reified T : Any> Provider<out T>.cached() = cached(objects)

    /**
     * Creates a cached provider for a list that evaluates and stores the value lazily.
     * The list is computed only once and reused for subsequent accesses.
     *
     * @receiver The list provider to be cached
     * @return A cached provider wrapping the original list provider
     */
    @JvmName("cachedList")
    internal inline fun <reified T : Any> Provider<out List<T>>.cached() = cached(objects)

    /**
     * Creates a cached provider for a map that evaluates and stores the value lazily.
     * The map is computed only once and reused for subsequent accesses.
     *
     * @receiver The map provider to be cached
     * @return A cached provider wrapping the original map provider
     */
    @JvmName("cachedMap")
    internal inline fun <reified K : Any, reified V : Any> Provider<out Map<K, V>>.cached() = cached(objects)

    /**
     * Creates a cached provider for a set that evaluates and stores the value lazily.
     * The set is computed only once and reused for subsequent accesses.
     *
     * @receiver The set provider to be cached
     * @return A cached provider wrapping the original set provider
     */
    @JvmName("cachedSet")
    internal inline fun <reified T : Any> Provider<out Set<T>>.cached() = cached(objects)

    /**
     * Lazy-initialized property responsible for resolving IntelliJ Platform caches.
     *
     * This variable initializes an instance of `IntelliJPlatformCacheResolver` with necessary parameters,
     * configurations, and dependencies. It serves as a utility to manage and resolve caching
     * requirements for IntelliJ Platform-based projects.
     *
     * The resolution process utilizes the `Parameters` class, which defines properties such as
     * `cacheDirectory` and `name`, derived from the provided `extensionProvider`.
     *
     * Dependencies and helpers such as `configurations`, `dependenciesHelperProvider`,
     * `extractorServiceProvider`, and the `objects` factory are required for its initialization.
     */
    internal val cacheResolver by lazy {
        val parameters = objects.newInstance<Parameters>().apply {
            cacheDirectory = extensionProvider.flatMap { it.caching.ides.path }
            name = extensionProvider.flatMap { it.caching.ides.name }
        }

        IntelliJPlatformCacheResolver(
            parameters = parameters,
            configurations = configurations,
            dependenciesHelperProvider = provider { this@IntelliJPlatformDependenciesHelper },
            extensionProvider = extensionProvider,
            extractorService = extractorServiceProvider,
            objects = objects,
        )
    }

    //<editor-fold desc="Metadata Accessors">

    /**
     * Provides access to the current IntelliJ Platform path.
     *
     * @param configurationName The IntelliJ Platform configuration name.
     */
    internal fun platformPathProvider(configurationName: String) =
        requestedIntelliJPlatformPaths.computeIfAbsent(configurationName) {
            val configuration = configurations[configurationName].apply {
                resolve()
                incoming.files
            }
            val requestedPlatform = requestedIntelliJPlatforms[configurationName]
            requestedPlatform.map {
                configuration.platformPath(it)
            }
        }

    internal fun ide(platformPath: Path) = gradle.registerClassLoaderScopedBuildService(IdesManagerService::class)
        .map { it.resolve(platformPath) }
        .get()

    //</editor-fold>

    //<editor-fold desc="Helper Methods">

    /**
     * Adds a dependency on a Jar bundled within the current IntelliJ Platform.
     *
     * @param pathProvider Path to the bundled Jar file, like `lib/testFramework.jar`.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param intellijPlatformConfigurationName The name of the IntelliJ Platform configuration that holds information about the current IntelliJ Platform instance.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addBundledLibrary(
        pathProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
        intellijPlatformConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(provider {
        val platformPath = platformPathProvider(intellijPlatformConfigurationName).get()
        val path = pathProvider.orNull
        requireNotNull(path) { "The `intellijPlatform.bundledLibrary` dependency helper was called with no `path` value provided." }

        createBundledLibrary(path, platformPath).apply(action).also {
            log.warn(
                """
                Do not use `bundledLibrary()` in production, as direct access to the IntelliJ Platform libraries is not recommended.
    
                It should only be used as a workaround in case the IntelliJ Platform Gradle Plugin is not aligned with the latest IntelliJ Platform classpath changes.
                """.trimIndent()
            )
        }
    }.cached())

    /**
     * Creates a request for the IntelliJ Platform with the specified configuration details.
     *
     * @param configurationsProvider IntelliJ Platform dependency configuration.
     * @param dependencyConfigurationName The name of the configuration that holds the requested IntelliJ Platform.
     * @param dependencyArchivesConfigurationName The name of the configuration that holds the IntelliJ Platform dependencies.
     * @param localArchivesConfigurationName The name of the configuration that holds the local IntelliJ Platform archives.
     * @param requiredConfigurationName The name of the configuration that holds the required IntelliJ Platform to be resolved before creating a new dependency.
     * @return A list of IntelliJ Platform requests.
     */
    internal fun addIntelliJPlatformCacheableDependencies(
        configurationsProvider: Provider<List<IntelliJPlatformDependencyConfiguration>>,
        dependencyConfigurationName: String,
        dependencyArchivesConfigurationName: String,
        localArchivesConfigurationName: String,
        requiredConfigurationName: String? = null,
    ) {
        val resolveConfiguration = {
            if (requiredConfigurationName != null && requiredConfigurationName != dependencyConfigurationName) {
                configurations[requiredConfigurationName].resolve()
            }
        }
        val requestsProvider = configurationsProvider.map { configurations ->
            resolveConfiguration()
            configurations.map {
                requestedIntelliJPlatforms.set(it, dependencyConfigurationName).get()
            }
        }.cached()

        configurations[localArchivesConfigurationName].dependencies.addAllLater(
            requestsProvider.map { requests ->
                requests.filter { it.useCache }
                    .map { requests ->
                        requests.let {
                            val localPath = cacheResolver.resolve(localArchivesConfigurationName) {
                                type = it.type
                                version = it.version
                                productMode = it.productMode
                                useInstaller = it.useInstaller
                                useCache = true
                            }
                            val platformPath = resolveArtifactPath(localPath)
                            createIntelliJPlatformLocal(platformPath)
                        }
                    }
            }.cached()
        )

        configurations[dependencyArchivesConfigurationName].dependencies.addAllLater(
            requestsProvider.map { requests ->
                requests
                    .filter { !it.useCache }
                    .map { createIntelliJPlatformDependency(it) }
            }.cached()
        )
    }

    /**
     * A base method for adding a dependency on the IntelliJ Platform.
     *
     * @param requestedIntelliJPlatformProvider The provider for the requested IntelliJ Platform.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action An optional action to be performed on the created dependency.
     * @throws GradleException
     */
    @Throws(GradleException::class)
    internal fun addIntelliJPlatformDependency(
        requestedIntelliJPlatformProvider: Provider<RequestedIntelliJPlatform>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY_ARCHIVE,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(provider {
        val requestedIntelliJPlatform = requestedIntelliJPlatformProvider.orNull
        requireNotNull(requestedIntelliJPlatform) { "The `intellijPlatform.dependency` dependency helper was called with no `requestedIntelliJPlatform` value provided." }

        createIntelliJPlatformDependency(requestedIntelliJPlatform).apply(action)
    }.cached())

    /**
     * A base method for adding a dependency on the IntelliJ Platform.
     *
     * @param notationsProvider The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action An optional action to be performed on the created dependency.
     */
    @Throws(GradleException::class)
    internal fun addIntelliJPluginVerifierIdes(
        notationsProvider: Provider<List<String>>,
        configurationName: String = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(provider {
        val notations = notationsProvider.get()

        notations.map {
            val (type, version) = it.parseIdeNotation()
            when (type) {
                IntelliJPlatformType.AndroidStudio -> createAndroidStudio(version)
                else -> createIntelliJPlatformInstaller(type, version)
            }.apply(action)
        }
    }.cached())

    /**
     * A base method for adding a dependency on a local IntelliJ Platform instance.
     *
     * @param localPathProvider The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     * @param configurationName The name of the configuration to add the dependency to.
     * @param intellijPlatformConfigurationName The name of the IntelliJ Platform configuration that holds information about the current IntelliJ Platform instance.
     * @param action An optional action to be performed on the created dependency.
     * @throws GradleException
     */
    @Throws(GradleException::class)
    internal fun addIntelliJPlatformLocalDependency(
        localPathProvider: Provider<*>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_LOCAL,
        intellijPlatformConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(provider {
        buildList {
            val localPath = localPathProvider.orNull ?: return@buildList
            val platformPath = resolveArtifactPath(localPath)
            val productInfo = platformPath.productInfo()

            val dependencyConfiguration =
                objects.newInstance<IntelliJPlatformDependencyConfiguration>(objects, extensionProvider).apply {
                    type = productInfo.type
                    version = productInfo.version
                    useInstaller = true
                    useCache = false
                    productMode = ProductMode.MONOLITH
                }
            requestedIntelliJPlatforms.set(dependencyConfiguration, intellijPlatformConfigurationName)

            createIntelliJPlatformLocal(platformPath).apply(::add).apply(action)
        }
    }.cached())

    /**
     * A base method for adding a dependency on a plugin for IntelliJ Platform.
     *
     * @param pluginsProvider The provider of the list containing triples with plugin identifier, version, and channel.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addIntelliJPlatformPluginDependencies(
        pluginsProvider: Provider<List<Triple<String, String, String>>>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(provider {
        val plugins = pluginsProvider.get()

        plugins.map { (id, version, group) ->
            require(id.isNotBlank()) {
                "The `intellijPlatform.plugins` dependency helper was called with a plugin with no `id` provided."
            }
            require(version.isNotBlank()) {
                """
                The `intellijPlatform.plugins` dependency helper was called with the `$id` plugin with no `version` provided.
                If you expect to add a dependency on a bundled plugin, use `intellijPlatform.bundledPlugin` or `intellijPlatform.bundledPlugins` instead.
                """.trimIndent()
            }
            createIntelliJPlatformPlugin(id, version, group).apply(action)
        }
    }.cached())

    /**
     * A base method for adding a dependency on plugins compatible with the current IntelliJ Platform version.
     * Each plugin ID is used to resolve the latest available version of the plugin using JetBrains Marketplace API.
     *
     * @param pluginsProvider The provider of the list containing plugin identifiers.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param intellijPlatformConfigurationName The name of the IntelliJ Platform configuration that holds information about the current IntelliJ Platform instance.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    @Incubating
    internal fun addCompatibleIntelliJPlatformPluginDependencies(
        pluginsProvider: Provider<List<String>>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY,
        intellijPlatformConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(provider {
        val plugins = pluginsProvider.orNull
        requireNotNull(plugins) { "The `intellijPlatform.compatiblePlugins` dependency helper was called with no `plugins` value provided." }

        val platformPath = platformPathProvider(intellijPlatformConfigurationName).orNull
        requireNotNull(platformPath) { "No IntelliJ Platform was resolved with the configuration name '${intellijPlatformConfigurationName}'." }

        val productInfo = platformPath.productInfo()

        plugins.map { pluginId ->
            val platformType = productInfo.productCode
            val platformVersion = productInfo.buildNumber

            val plugin = pluginRepository.pluginManager.searchCompatibleUpdates(
                build = "$platformType-$platformVersion",
                xmlIds = listOf(pluginId),
            ).firstOrNull()
                ?: throw GradleException("No plugin update with id='$pluginId' compatible with '$platformType-$platformVersion' found in JetBrains Marketplace")

            createIntelliJPlatformPlugin(
                plugin.pluginXmlId,
                plugin.version,
                Dependencies.MARKETPLACE_GROUP,
            ).apply(action)
        }
    }.cached())

    /**
     * A base method for adding a dependency on an IntelliJ Platform bundled plugin.
     *
     * @param bundledPluginsProvider The provider of the list containing bundled plugin identifiers.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param intellijPlatformConfigurationName The name of the IntelliJ Platform configuration that holds information about the current IntelliJ Platform instance.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addIntelliJPlatformBundledPluginDependencies(
        bundledPluginsProvider: Provider<List<String>>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS,
        intellijPlatformConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(provider {
        val bundledPlugins = bundledPluginsProvider.orNull
        requireNotNull(bundledPlugins) { "The `intellijPlatform.bundledPlugins` dependency helper was called with no `bundledPlugins` value provided." }

        val platformPath = platformPathProvider(intellijPlatformConfigurationName).orNull
        requireNotNull(platformPath) { "No IntelliJ Platform was resolved with the configuration name '${intellijPlatformConfigurationName}'." }

        bundledPlugins
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { createIntelliJPlatformBundledPlugin(platformPath, it) }
            .onEach(action)
    }.cached())

    /**
     * A base method for adding a dependency on an IntelliJ Platform bundled module.
     *
     * @param bundledModulesProvider The provider of the list containing bundled module identifiers.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param intellijPlatformConfigurationName The name of the IntelliJ Platform configuration that holds information about the current IntelliJ Platform instance.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addIntelliJPlatformBundledModuleDependencies(
        bundledModulesProvider: Provider<List<String>>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_BUNDLED_MODULES,
        intellijPlatformConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(provider {
        val bundledModules = bundledModulesProvider.orNull
        requireNotNull(bundledModules) { "The `intellijPlatform.bundledModules` dependency helper was called with no `bundledModules` value provided." }

        val platformPath = platformPathProvider(intellijPlatformConfigurationName).orNull
        requireNotNull(platformPath) { "No IntelliJ Platform was resolved with the configuration name '${intellijPlatformConfigurationName}'." }

        bundledModules
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { createIntelliJPlatformBundledModule(it, platformPath) }
            .onEach(action)
    }.cached())

    /**
     * Adds Compose UI-related bundled modules depending on the current IntelliJ Platform build.
     *
     * Because composeUI() in the extension layer has no access to product info, this helper
     * inspects the platform located at [intellijPlatformConfigurationName] and decides which
     * modules to include.
     */
    internal fun addComposeUiDependencies(
        configurationName: String = Configurations.INTELLIJ_PLATFORM_BUNDLED_MODULES,
        intellijPlatformConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(provider {
        val platformPath = platformPathProvider(intellijPlatformConfigurationName).orNull
        requireNotNull(platformPath) { "No IntelliJ Platform was resolved with the configuration name '${intellijPlatformConfigurationName}'." }

        val productInfo = platformPath.productInfo()
        val major = productInfo.buildNumber.toVersion().major

        val modules = buildList {
            // 2024.3+
            if (major >= 243) {
                add("intellij.libraries.skiko")
                add("intellij.platform.compose")
            }

            // 2025.1+
            if (major >= 251) {
                add("intellij.libraries.compose.foundation.desktop")
                add("intellij.platform.jewel.foundation")
                add("intellij.platform.jewel.ui")
                add("intellij.platform.jewel.ideLafBridge")
            }

            // 2025.3+
            if (major >= 253) {
                add("intellij.libraries.compose.runtime.desktop")
            }
        }

        modules
            .map { createIntelliJPlatformBundledModule(it, platformPath) }
            .onEach(action)
    }.cached())

    /**
     * A base method for adding a dependency on a local plugin for IntelliJ Platform.
     *
     * @param localPathProvider The provider of the path to the local plugin.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param intellijPlatformConfigurationName The name of the IntelliJ Platform configuration that holds information about the current IntelliJ Platform instance.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addIntelliJPlatformLocalPluginDependency(
        localPathProvider: Provider<*>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL,
        intellijPlatformConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(provider {
        val localPath = localPathProvider.orNull
        requireNotNull(localPath) { "The `intellijPlatform.localPlugin` dependency helper was called with no `localPath` value provided." }

        val platformPath = platformPathProvider(intellijPlatformConfigurationName).orNull
        requireNotNull(platformPath) { "No IntelliJ Platform was resolved with the configuration name '${intellijPlatformConfigurationName}'." }

        createIntelliJPlatformLocalPlugin(localPath, platformPath).apply(action)
    }.cached())

    /**
     * A base method for adding a project dependency on a local plugin for IntelliJ Platform.
     *
     * @param dependency The plugin project dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addIntelliJPlatformLocalPluginProjectDependency(
        dependency: ProjectDependency,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.add(dependency.apply(action))

    /**
     * A base method for adding a project dependency on a module to be moved into `lib/modules` by [PrepareSandboxTask].
     *
     * @param dependency Plugin module dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addIntelliJPlatformPluginModuleDependency(
        dependency: Dependency,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.add(dependency.apply(action))

    /**
     * A base method for adding a project dependency on a module to be merged into the main plugin Jar archive by [ComposedJarTask].
     *
     * @param dependency Plugin composed module dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addIntelliJPlatformPluginComposedModuleDependency(
        dependency: Dependency,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGIN_COMPOSED_MODULE,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.add(dependency.apply(action))

    /**
     * Adds a dependency on a Java Compiler used, i.e., for running code instrumentation.
     *
     * @param versionProvider The provider of the Java Compiler version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addJavaCompilerDependency(
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_JAVA_COMPILER,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        versionProvider.map { version ->
            createJavaCompiler(version).apply(action)
        }.cached(),
    )

    /**
     * A base method for adding a dependency on JetBrains Runtime.
     *
     * @param explicitVersionProvider The provider for the explicit version of the JetBrains Runtime.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addJetBrainsRuntimeDependency(
        explicitVersionProvider: Provider<String>,
        configurationName: String = Configurations.JETBRAINS_RUNTIME_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        explicitVersionProvider.map { explicitVersion ->
            createJetBrainsRuntime(explicitVersion).apply(action)
        }.cached(),
    )

    /**
     * A base method for adding a dependency on JetBrains Runtime.
     *
     * @param configurationName The name of the configuration to add the dependency to.
     * @param intellijPlatformConfigurationName The name of the IntelliJ Platform configuration that holds information about the current IntelliJ Platform instance.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addJetBrainsRuntimeObtainedDependency(
        configurationName: String = Configurations.JETBRAINS_RUNTIME_DEPENDENCY,
        intellijPlatformConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(provider {
        createJetBrainsRuntimeObtainedDependency(intellijPlatformConfigurationName).onEach(action)
    }.cached())

    /**
     * A base method for adding a dependency on JetBrains Runtime.
     *
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addJetBrainsRuntimeLocalDependency(
        localPathProvider: Provider<*>,
        configurationName: String = Configurations.JETBRAINS_RUNTIME_LOCAL_INSTANCE,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(provider {
        val localPath = localPathProvider.orNull
        requireNotNull(localPath) { "The `intellijPlatform.jetBrainsRuntimeLocal` dependency helper was called with no `localPath` value provided." }

        val artifactPath = resolvePath(localPath)
            .takeIf { it.exists() }
            ?.resolveJavaRuntimeDirectory()
            .let { requireNotNull(it) { "Specified localPath '$localPath' doesn't exist." } }

        createJetBrainsRuntimeLocal(artifactPath).apply(action)
    }.cached())

    /**
     * A base method for adding a dependency on IntelliJ Plugin Verifier.
     *
     * @param versionProvider The provider of the IntelliJ Plugin Verifier version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addPluginVerifierDependency(
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLUGIN_VERIFIER,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(provider {
        val version = versionProvider.orNull
        requireNotNull(version) { "The `intellijPlatform.pluginVerifier` dependency helper was called with no `version` value provided." }

        createPluginVerifier(version).apply(action)
    }.cached())

    /**
     * Adds a dependency on the `test-framework` library required for testing plugins.
     *
     * In rare cases, when the presence of bundled `lib/testFramework.jar` library is necessary,
     * it is possible to attach it by using the [TestFrameworkType.Bundled] type.
     *
     * This dependency belongs to IntelliJ Platform repositories.
     *
     * @param type The TestFramework type provider.
     * @param versionProvider The version of the TestFramework.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param intellijPlatformConfigurationName The name of the IntelliJ Platform configuration that holds information about the current IntelliJ Platform instance.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addTestFrameworkDependency(
        type: TestFrameworkType,
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES,
        intellijPlatformConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(provider {
        val platformPath = platformPathProvider(intellijPlatformConfigurationName).orNull
        requireNotNull(platformPath) { "No IntelliJ Platform was resolved with the configuration name '${intellijPlatformConfigurationName}'." }

        when (type) {
            TestFrameworkType.Bundled -> type.coordinates.map {
                createBundledLibrary(it.artifactId, platformPath)
            }

            else -> {
                val version = versionProvider.orNull
                requireNotNull(version) { "The `intellijPlatform.testFramework` dependency helper was called with no `version` value provided." }

                type.coordinates.map {
                    createPlatformDependency(it, version, platformPath)
                }
            }
        }.onEach(action)
    }.cached())

    /**
     * Adds a dependency on the IntelliJ Platform dependency.
     *
     * This dependency belongs to IntelliJ Platform repositories.
     *
     * @param coordinates The coordinates of the IntelliJ Platform dependency.
     * @param versionProvider The version of the IntelliJ Platform dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param intellijPlatformConfigurationName The name of the IntelliJ Platform configuration that holds information about the current IntelliJ Platform instance.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addPlatformDependency(
        coordinates: Coordinates,
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
        intellijPlatformConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(provider {
        val version = versionProvider.orNull
        requireNotNull(version) { "The `intellijPlatform.platformDependency`/`intellijPlatform.testPlatformDependency` dependency helper was called with no `version` value provided." }

        val platformPath = platformPathProvider(intellijPlatformConfigurationName).orNull
        requireNotNull(platformPath) { "No IntelliJ Platform was resolved with the configuration name '${intellijPlatformConfigurationName}'." }

        createPlatformDependency(coordinates, version, platformPath).apply(action)
    }.cached())

    /**
     * A base method for adding a dependency on Marketplace ZIP Signer.
     *
     * @param versionProvider The provider of the Marketplace ZIP Signer version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addZipSignerDependency(
        versionProvider: Provider<String>,
        configurationName: String = Configurations.MARKETPLACE_ZIP_SIGNER,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(provider {
        val version = versionProvider.orNull
        requireNotNull(version) { "The `intellijPlatform.zipSigner` dependency helper was called with no `version` value provided." }

        createMarketplaceZipSigner(version).apply(action)
    }.cached())

    /**
     * A base method for adding a dependency on Robot Server Plugin.
     *
     * @param versionProvider The provider of the Robot Server Plugin version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addRobotServerPluginDependency(
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(provider {
        val version = versionProvider.orNull
        requireNotNull(version) { "The `intellijPlatform.robotServerPlugin` dependency helper was called with no `version` value provided." }

        createRobotServerPlugin(version).apply(action)
    }.cached())

    internal fun createProductReleasesValueSource(configure: ProductReleasesValueSource.FilterParameters.() -> Unit) =
        providers.of(ProductReleasesValueSource::class) {
            parameters.jetbrainsIdesUrl = providers[GradleProperties.ProductsReleasesJetBrainsIdesUrl]
            parameters.androidStudioUrl = providers[GradleProperties.ProductsReleasesAndroidStudioUrl]

            parameters(configure)
        }

    //</editor-fold>

    //<editor-fold desc="DependencyHelper Extensions">

    /**
     * Creates IntelliJ Platform dependency.
     *
     * @param requestedIntelliJPlatform requested IntelliJ Platform.
     */
    private fun createIntelliJPlatformDependency(requestedIntelliJPlatform: RequestedIntelliJPlatform) =
        with(requestedIntelliJPlatform) {
            runCatching { type.validateVersion(version) }.onFailure { log.error(it.message.orEmpty()) }

            when {
                productMode == ProductMode.FRONTEND -> createJetBrainsClient(type, version)
                type == IntelliJPlatformType.AndroidStudio -> createAndroidStudio(version)
                else -> when {
                    useInstaller -> createIntelliJPlatformInstaller(type, version)
                    else -> createIntelliJPlatform(type, version)
                }
            }
        }

    /**
     * Creates Android Studio dependency.
     *
     * @param version Android Studio version.
     */
    private fun createAndroidStudio(version: String): Dependency {
        val type = IntelliJPlatformType.AndroidStudio
        runCatching { type.validateVersion(version) }.onFailure { log.error(it.message.orEmpty()) }

        val downloadLink = providers.of(AndroidStudioDownloadLinkValueSource::class) {
            parameters {
                androidStudioUrl = providers[GradleProperties.ProductsReleasesAndroidStudioUrl]
                androidStudioVersion = version
            }
        }.orNull

        requireNotNull(downloadLink) { "Couldn't resolve Android Studio download URL for version: '$version'" }
        requireNotNull(type.installer) { "Specified type '$type' has no artifact coordinates available." }

        val group = type.installer.groupId
        val name = type.installer.artifactId
        val (classifier, extension) = downloadLink.substringAfter("$version-").split(".", limit = 2)

        return dependencyFactory.create(group, name, version, classifier, extension)
    }

    /**
     * Creates JetBrains Client dependency.
     *
     * @param version JetBrains Client version.
     */
    private fun createJetBrainsClient(type: IntelliJPlatformType, version: String): Dependency {
        val jetBrainsClientType = requireNotNull(IntelliJPlatformType.JetBrainsClient.installer)
        runCatching { type.validateVersion(version) }.onFailure { log.error(it.message.orEmpty()) }
        runCatching { IntelliJPlatformType.JetBrainsClient.validateVersion(version) }.onFailure { log.error(it.message.orEmpty()) }

        val buildNumberProvider = providers.of(ProductReleaseBuildValueSource::class) {
            parameters.productsReleasesCdnBuildsUrl = providers[GradleProperties.ProductsReleasesCdnBuildsUrl]
            parameters.version = version
            parameters.type = type
        }

        val (extension, classifier) = with(OperatingSystem.current()) {
            val arch = System.getProperty("os.arch").takeIf { it == "aarch64" }
            when {
                isLinux -> ArtifactType.TAR_GZ to arch
                isWindows -> ArtifactType.ZIP to when {
                    arch == null -> "jbr.win"
                    else -> "$arch.jbr.win"
                }

                isMacOsX -> ArtifactType.SIT to arch
                else -> throw GradleException("Unsupported operating system: $name")
            }
        }.let { (type, classifier) -> type.toString() to classifier }

        val group = jetBrainsClientType.groupId
        val name = jetBrainsClientType.artifactId

        return dependencyFactory.create(group, name, buildNumberProvider.get(), classifier, extension)
    }

    /**
     * Creates a [Dependency] using a Jar file resolved in [platformPath] with [path].
     *
     * @param path Relative path to the library, like: `lib/testFramework.jar`.
     * @param platformPath Path to the current IntelliJ Platform.
     */
    private fun createBundledLibrary(path: String, platformPath: Path) = dependencyFactory.create(
        objects.fileCollection().from(platformPath.resolve(path)),
    )

    /**
     * Creates IntelliJ Platform dependency based on the provided version and type.
     *
     * @param version IntelliJ Platform version.
     * @param type IntelliJ Platform type.
     */
    private fun createIntelliJPlatform(type: IntelliJPlatformType, version: String): Dependency {
        requireNotNull(type.maven) { "Specified type '$type' has no artifact coordinates available." }

        val group = type.maven.groupId
        val name = type.maven.artifactId

        return dependencyFactory.create(group, name, version)
    }

    /**
     * Creates IntelliJ Platform dependency based on the provided version and type.
     *
     * @param version IntelliJ Platform version.
     * @param type IntelliJ Platform type.
     */
    private fun createIntelliJPlatformInstaller(
        type: IntelliJPlatformType,
        version: String,
    ): Dependency {
        requireNotNull(type.installer) { "Specified type '$type' has no artifact coordinates available." }

        val group = type.installer.groupId
        val name = type.installer.artifactId

        if (type == IntelliJPlatformType.Rider) {
            log.warn("Using Rider as a target IntelliJ Platform with `useInstaller = true` is currently not supported, please set `useInstaller = false` instead. See: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1852")
        }

        val (extension, classifier) = with(OperatingSystem.current()) {
            val arch = System.getProperty("os.arch").takeIf { it == "aarch64" }
            when {
                isWindows -> ArtifactType.ZIP to "win"
                isLinux -> ArtifactType.TAR_GZ to arch
                isMacOsX -> ArtifactType.DMG to arch
                else -> throw GradleException("Unsupported operating system: $name")
            }
        }.let { (type, classifier) -> type.toString() to classifier }

        return dependencyFactory.create(group, name, version, classifier, extension)
    }

    /**
     * Creates a dependency on a local IntelliJ Platform instance.
     *
     * @param artifactPath Path to the local IntelliJ Platform.
     */
    private fun createIntelliJPlatformLocal(artifactPath: Path): Dependency {
        val localProductInfo = artifactPath.productInfo().apply {
            validateSupportedVersion()
        }

        val type = localProductInfo.type
        // It is crucial to use the IDE type + build number to the version.
        // Because if UI & IC are used by different submodules in the same build, they might rewrite each other's Ivy
        // XML files, which might have different optional transitive dependencies defined due to IC having fewer plugins.
        val version = localProductInfo.fullVersion

        writeIvyModule(Dependencies.LOCAL_IDE_GROUP, type.code, version, artifactPath) {
            IvyModule(
                info = IvyModule.Info(
                    organisation = Dependencies.LOCAL_IDE_GROUP,
                    module = type.code,
                    revision = version,
                ),
                publications = listOf(artifactPath.toAbsolutePathIvyArtifact()),
            )
        }

        val group = Dependencies.LOCAL_IDE_GROUP
        val name = type.code

        return dependencyFactory.create(group, name, version)
    }

    /**
     * Creates a dependency for an IntelliJ Platform plugin.
     *
     * @param pluginId The ID of the plugin.
     * @param version The version of the plugin.
     * @param group The channel of the plugin. Can be null or empty for the default channel.
     */
    private fun createIntelliJPlatformPlugin(
        pluginId: String,
        version: String,
        group: String,
    ): Dependency {
        val groupId = group.substringBefore('@').ifEmpty { Dependencies.MARKETPLACE_GROUP }
        val channel = group.substringAfter('@', "")

        val group = "$channel.$groupId".trim('.')
        val name = pluginId.trim()

        return dependencyFactory.create(group, name, version)
    }

    /**
     * Creates a dependency for an IntelliJ platform bundled plugin.
     *
     * @param id The ID of the bundled plugin.
     */
    private fun createIntelliJPlatformBundledPlugin(platformPath: Path, id: String): Dependency {
        val productInfo = platformPath.productInfo()
        val ide = ide(platformPath)
        val plugin = ide.findPluginById(id) ?: ide.findPluginByModule(id)

        requireNotNull(plugin) {
            val unresolvedPluginId = when (id) {
                "copyright" -> "Use correct plugin ID 'com.intellij.copyright' instead of 'copyright'."
                "css", "css-impl" -> "Use correct plugin ID 'com.intellij.css' instead of 'css'/'css-impl'."
                "DatabaseTools" -> "Use correct plugin ID 'com.intellij.database' instead of 'DatabaseTools'."
                "Groovy" -> "Use correct plugin ID 'org.intellij.groovy' instead of 'Groovy'."
                "gradle" -> "Use correct plugin ID 'com.intellij.gradle' instead of 'gradle'."
                "java" -> "Use correct plugin ID 'com.intellij.java' instead of 'java'."
                "Kotlin" -> "Use correct plugin ID 'org.jetbrains.kotlin' instead of 'Kotlin'."
                "markdown" -> "Use correct plugin ID 'org.intellij.plugins.markdown' instead of 'markdown'."
                "maven" -> "Use correct plugin ID 'org.jetbrains.idea.maven' instead of 'maven'."
                "yaml" -> "Use correct plugin ID 'org.jetbrains.plugins.yaml' instead of 'yaml'."
                else -> "Could not find bundled plugin with ID: '$id'."
            }
            "$unresolvedPluginId See https://jb.gg/ij-plugin-dependencies."
        }

        val artifactPath = requireNotNull(plugin.originalFile) {
            "The '$id' entry refers to a bundled plugin, but it is actually a bundled module. " +
                    "Use bundledModule(\"$id\") instead of bundledPlugin(\"$id\")."
        }
        // It is crucial to use the IDE type + build number to the version.
        // Because if UI & IC are used by different submodules in the same build, they might rewrite each other's Ivy
        // XML files, which might have different optional transitive dependencies defined due to IC having fewer plugins.
        // Should be the same as [collectDependencies]
        val version = productInfo.fullVersion

        writeIvyModule(Dependencies.BUNDLED_PLUGIN_GROUP, id, version, artifactPath) {
            IvyModule(
                info = IvyModule.Info(
                    organisation = Dependencies.BUNDLED_PLUGIN_GROUP,
                    module = id,
                    revision = version,
                ),
                publications = artifactPath.toIvyArtifacts(metadataRulesModeProvider, platformPath),
                dependencies = plugin.collectDependencies(platformPath),
            )
        }

        val group = Dependencies.BUNDLED_PLUGIN_GROUP

        return dependencyFactory.create(group, id, version)
    }

    /**
     * Creates a dependency for an IntelliJ platform bundled module.
     *
     * @param id The ID of the bundled module.
     * @param platformPath The path to the current IntelliJ Platform.
     */
    private fun createIntelliJPlatformBundledModule(id: String, platformPath: Path): Dependency {
        val bundledModule = ide(platformPath).findPluginById(id)
        requireNotNull(bundledModule) { "Specified bundledModule '$id' doesn't exist." }

        val classpath = bundledModule.classpath.paths.map { it.safePathString }
        val (group, name, version) = writeBundledModuleDependency(id, classpath, platformPath)

        return dependencyFactory.create(group, name, version)
    }

    /**
     * Collects all dependencies on plugins or modules of the current [IdePlugin].
     * The [alreadyProcessedOrProcessing] parameter is a list of already traversed entities, used to avoid circular dependencies when walking recursively.
     *
     * @param platformPath The path to the current IntelliJ Platform.
     * @param alreadyProcessedOrProcessing IDs of already traversed plugins or modules.
     */
    private fun IdePlugin.collectDependencies(
        platformPath: Path,
        alreadyProcessedOrProcessing: List<String> = emptyList(),
    ): List<IvyModule.Dependency> {
        // It is crucial to use the IDE type + build number to the version.
        // Because if UI & IC are used by different submodules in the same build, they might rewrite each other's Ivy
        // XML files, which might have different optional transitive dependencies defined due to IC having fewer plugins.
        // Should be the same as [createIntelliJPlatformBundledPlugin]
        val ide = ide(platformPath)
        val id = requireNotNull(pluginId)
        val version = ide.version.toString()

        val modulesIds = modulesDescriptors.asSequence().filter { it.loadingRule.required }.map { it.name }
        val dependenciesIds = dependencies.asSequence().filter { !it.isOptional }.map { it.id }
        val ids = (modulesIds + dependenciesIds).mapNotNull { ide.findPluginById(it) }

        return ids
            .mapTo(ArrayList()) {
                val name = requireNotNull(it.pluginId)
                val group = when {
                    it is IdeModule -> Dependencies.BUNDLED_MODULE_GROUP
                    else -> Dependencies.BUNDLED_PLUGIN_GROUP
                }
                val publications = when {
                    it is IdeModule -> it.classpath.paths.flatMap { path ->
                        path.toIvyArtifacts(metadataRulesModeProvider, platformPath)
                    }

                    else -> requireNotNull(it.originalFile).toIvyArtifacts(metadataRulesModeProvider, platformPath)
                }

                val doesNotDependOnSelf = id != it.pluginId
                val hasNeverBeenSeen = it.pluginId !in alreadyProcessedOrProcessing

                if (doesNotDependOnSelf && hasNeverBeenSeen) {
                    writeIvyModule(group, name, version, it.originalFile) {
                        IvyModule(
                            info = IvyModule.Info(group, name, version),
                            publications = publications,
                            dependencies = it.collectDependencies(platformPath, alreadyProcessedOrProcessing + id),
                        )
                    }
                }

                IvyModule.Dependency(group, name, version)
            }
    }

    /**
     * Writes a bundled module Ivy XML configuration file.
     *
     * @param name The name of the module.
     * @param classPath A list of class path entries for the module.
     * @param platformPath The path to the current IntelliJ Platform.
     * @return A [Triple] containing a dependency group, name, and version
     */
    private fun writeBundledModuleDependency(
        name: String,
        classPath: Collection<String>,
        platformPath: Path,
    ): Triple<String, String, String> {
        val group = Dependencies.BUNDLED_MODULE_GROUP
        // It is crucial to use the IDE type + build number to the version.
        // Because if UI & IC are used by different submodules in the same build, they might rewrite each other's Ivy
        // XML files, which might have different optional transitive dependencies defined due to IC having fewer plugins.
        val productInfo = platformPath.productInfo()
        val version = productInfo.fullVersion
        val artifacts = classPath.flatMap {
            platformPath.resolve(it).toIvyArtifacts(metadataRulesModeProvider, platformPath)
        }

        /**
         * For bundled modules, usually we don't have a path to their archive (jar), since we get them from [ProductInfo.layout], which does not have a path.
         * They're located in "IDE/lib/modules" and duplication shouldn't be an issue, so we can try to ignore the path comparison.
         */
        writeIvyModule(group, name, version, null) {
            IvyModule(
                info = IvyModule.Info(group, name, version),
                publications = artifacts,
            )
        }

        return Triple(group, name, version)
    }

    /**
     * Creates a dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     * @param platformPath The path to the current IntelliJ Platform.
     */
    private fun createIntelliJPlatformLocalPlugin(localPath: Any, platformPath: Path): Dependency {
        val productInfo = platformPath.productInfo()
        val artifactPath = resolvePath(localPath)
            .takeIf { it.exists() }
            .let { requireNotNull(it) { "Specified localPath '$localPath' doesn't exist." } }

        val plugin by lazy {
            val pluginPath = when {
                artifactPath.isDirectory() -> artifactPath.resolvePluginPath()
                else -> artifactPath
            }
            pluginManager.safelyCreatePlugin(pluginPath, suppressPluginProblems = true).getOrThrow()
        }

        // It is crucial to use the IDE type + build number to the version.
        // Because if UI & IC are used by different submodules in the same build, they might rewrite each other's Ivy
        // XML files, which might have different optional transitive dependencies defined due to IC having fewer plugins.
        val version = productInfo.fullVersion + "-" + (plugin.pluginVersion ?: "0.0.0")
        val group = Dependencies.LOCAL_PLUGIN_GROUP
        val name = plugin.pluginId ?: artifactPath.name

        writeIvyModule(Dependencies.LOCAL_PLUGIN_GROUP, name, version, artifactPath) {
            IvyModule(
                info = IvyModule.Info(
                    organisation = Dependencies.LOCAL_PLUGIN_GROUP,
                    module = name,
                    revision = version,
                ),
                publications = listOf(artifactPath.toAbsolutePathIvyArtifact()),
            )
        }

        return dependencyFactory.create(group, name, version)
    }

    /**
     * Creates a dependency on a local JetBrains Runtime instance.
     *
     * @param artifactPath Path to the local JetBrains Runtime.
     */
    private fun createJetBrainsRuntimeLocal(artifactPath: Path): Dependency {
        val runtimeMetadata = providers.of(JavaRuntimeMetadataValueSource::class) {
            parameters {
                executable = layout.file(
                    provider {
                        artifactPath.resolveJavaRuntimeExecutable().toFile()
                    },
                )
            }
        }.get()

        // E.g.: JBR-21.0.4+13-509.17-jcef
        val javaVendorVersion = runtimeMetadata["java.vendor.version"]
        requireNotNull(javaVendorVersion)

        // E.g.: 21.0.4
        val javaVersion = runtimeMetadata["java.version"]
        requireNotNull(javaVersion)

        val group = Dependencies.LOCAL_JETBRAINS_RUNTIME_GROUP

        // E.g.: JBR
        val name = javaVendorVersion.substringBefore(javaVersion).trim('-')
        // It is crucial to use the full version.
        // Because if someone decides to bump JBR within the same marketing version,
        // but in the next build – it’ll still refer to the incorrect version as they’ll be treated equally.
        // They would rewrite each other's Ivy XML files since they would have the same name.
        // E.g.: 21.0.4+13-509.17-jcef
        val version = javaVendorVersion.removePrefix(name).trim('-')

        writeIvyModule(Dependencies.LOCAL_JETBRAINS_RUNTIME_GROUP, name, version, artifactPath) {
            IvyModule(
                info = IvyModule.Info(
                    organisation = Dependencies.LOCAL_JETBRAINS_RUNTIME_GROUP,
                    module = name,
                    revision = version,
                ),
                publications = listOf(artifactPath.toAbsolutePathIvyArtifact()),
            )
        }

        return dependencyFactory.create(group, name, version)
    }

    /**
     * Creates Java Compiler dependency.
     *
     * @param version Java Compiler dependency version
     */
    internal fun createJavaCompiler(version: String = Constraints.CLOSEST_VERSION) =
        createDependency(
            coordinates = Coordinates("com.jetbrains.intellij.java", "java-compiler-ant-tasks"),
            version = version,
        )

    /**
     * Creates JetBrains Runtime (JBR) dependency.
     *
     * @param version JetBrains Runtime (JBR) dependency version
     */
    internal fun createJetBrainsRuntime(version: String) =
        dependencyFactory.create("com.jetbrains", "jbr", version, null, "tar.gz")

    /**
     * Creates IntelliJ Plugin Verifier CLI tool dependency.
     *
     * @param version IntelliJ Plugin Verifier CLI tool dependency version
     */
    internal fun createPluginVerifier(version: String = Constraints.LATEST_VERSION) =
        createDependency(
            coordinates = Coordinates("org.jetbrains.intellij.plugins", "verifier-cli"),
            version = version,
            classifier = "all",
            extension = "jar",
        )

    /**
     * Adds a dependency on a Marketplace ZIP Signer required for signing plugin with [SignPluginTask].
     *
     * @param version Marketplace ZIP Signer version.
     */
    internal fun createMarketplaceZipSigner(version: String = Constraints.LATEST_VERSION) =
        createDependency(
            coordinates = Coordinates("org.jetbrains", "marketplace-zip-signer"),
            version = version,
            classifier = "cli",
            extension = "jar",
        )

    /**
     * Adds a dependency on a Compose Hot Reload agent with [RunIdeTask].
     *
     * @param version agent version.
     */
    internal fun createComposeHotReloadAgent(version: String = Constraints.LATEST_VERSION) =
        createDependency(
            coordinates = Coordinates("org.jetbrains.compose.hot-reload", "hot-reload-agent"),
            version = version,
            classifier = "standalone",
            extension = "jar",
        )

    /**
     * Adds a dependency on a Robot Server Plugin required for signing plugin with [TestIdeUiTask].
     *
     * @param version Robot Server Plugin version.
     */
    internal fun createRobotServerPlugin(version: String) =
        createDependency(
            coordinates = Coordinates("com.intellij.remoterobot", "robot-server-plugin"),
            version = version,
            extension = "zip",
        )

    /**
     * Returns JetBrains Runtime version obtained using the currently used IntelliJ Platform.
     *
     * @param intellijPlatformConfigurationName The name of the IntelliJ Platform configuration that holds information about the current IntelliJ Platform instance.
     */
    internal fun obtainJetBrainsRuntimeVersion(intellijPlatformConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY): String? {
        val dependencies = runCatching {
            val platformPath = platformPathProvider(intellijPlatformConfigurationName).get()
            platformPath.resolve("dependencies.txt").takeIf { it.exists() }
        }.getOrNull() ?: return null

        val version = FileReader(dependencies.toFile()).use { reader ->
            with(Properties()) {
                load(reader)
                getProperty("runtimeBuild") ?: getProperty("jdkBuild")
            }
        } ?: return null

        return buildJetBrainsRuntimeVersion(version)
    }

    /**
     * Creates a list property of dependencies containing the JetBrains Runtime dependency.
     * The runtime version is obtained from the IntelliJ Platform configuration.
     *
     * @param intellijPlatformConfigurationName Configuration name for IntelliJ Platform dependency, defaults to "intellijPlatformDependency"
     * @return List property containing JetBrains Runtime dependency if a version could be obtained, empty list otherwise
     */
    internal fun createJetBrainsRuntimeObtainedDependency(intellijPlatformConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY): List<Dependency> =
        buildList {
            obtainJetBrainsRuntimeVersion(intellijPlatformConfigurationName)
                ?.let { add(createJetBrainsRuntime(it)) }
        }

    /**
     * Collects all required jars into a single artificial dependency to satisfy the test runtime.
     * Due to the tests classpath loader, it is required to provide explicitly all dependencies on
     * bundled plugins and modules so tests can run with all runtime elements loaded.
     *
     * See https://youtrack.jetbrains.com/issue/IJPL-180516/Gradle-tests-fail-without-transitive-modules-jars-of-com.intellij-in-classpath
     *
     * @param platformPath The path to the current IntelliJ Platform.
     */
    internal fun createIntelliJPlatformTestRuntime(platformPath: Path): Dependency {
        val id = "intellij-platform-test-runtime"
        val ide = ide(platformPath)

        val classpath = ide.findPluginById("com.intellij")
            ?.classpath
            ?.paths
            .orEmpty()
            .map { it.safePathString }.toSet()

        val (group, name, version) = writeBundledModuleDependency(id, classpath, platformPath)
        return dependencyFactory.create(group, name, version)
    }

    /**
     * Creates a dependency.
     *
     * @param coordinates Dependency coordinates.
     * @param version Dependency version.
     * @param classifier Optional dependency classifier.
     * @param extension Optional dependency extension.
     * @param intellijPlatformConfigurationName The name of the IntelliJ Platform configuration that holds information about the current IntelliJ Platform instance.
     */
    private fun createDependency(
        coordinates: Coordinates,
        version: String,
        classifier: String? = null,
        extension: String? = null,
        intellijPlatformConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
    ) = dependencyFactory.create(coordinates.groupId, coordinates.artifactId, null, classifier, extension).apply {
        val requestedIntelliJPlatform by lazy {
            requestedIntelliJPlatforms[intellijPlatformConfigurationName].get()
        }

        val resolvedVersion = when {
            version == Constraints.CLOSEST_VERSION && requestedIntelliJPlatform.isNightly -> Constraints.PLATFORM_VERSION
            else -> version
        }

        when (resolvedVersion) {
            Constraints.PLATFORM_VERSION ->
                version {
                    prefer(requestedIntelliJPlatform.version)
                }

            Constraints.CLOSEST_VERSION ->
                version {
                    val buildNumber by lazy {
                        runCatching {
                            val platformPath = platformPathProvider(intellijPlatformConfigurationName).get()
                            val productInfo = platformPath.productInfo()
                            productInfo.buildNumber.toVersion()

                        }.getOrDefault(Version()) // fallback to 0.0.0 if IntelliJ Platform is missing
                    }

                    strictly("[${buildNumber.major}, $buildNumber]")
                    prefer("$buildNumber")
                }

            Constraints.LATEST_VERSION ->
                version {
                    prefer("+")
                }

            else ->
                version {
                    prefer(resolvedVersion)
                }
        }
    }

    /**
     * Creates and writes the Ivy module file for the specified group, artifact, and version, if absent.
     *
     * @param group The group identifier for the Ivy module.
     * @param artifact The artifact name for the Ivy module.
     * @param version The version of the Ivy module.
     * @param block A lambda that returns an instance of IvyModule to be serialized into the file.
     * @param artifactPath Path of the IvyModule, stored in cache to prevent path conflicts for Ivy XML coordinates.
     *
     * @see writtenIvyModules
     */
    private fun writeIvyModule(
        group: String,
        artifact: String,
        version: String,
        artifactPath: Path?,
        block: () -> IvyModule,
    ): IvyModule {
        val fileName = "$version/$group-$artifact-$version.xml"

        // See comments on writtenIvyModules
        val cachedValue = writtenIvyModules[fileName]
        if (cachedValue is IvyModule && null == artifactPath) {
            log.info("An attempt to rewrite an already created Ivy module '${fileName}' has been detected, the cached value will be used.")
            return cachedValue
        }

        if (cachedValue is Pair<*, *> && artifactPath != null) {
            val cachedIvyModule = cachedValue.first
            val cachedModulePath = cachedValue.second

            if (cachedIvyModule is IvyModule && cachedModulePath is Path) {
                val cachedModulePathString = cachedModulePath.safePathString
                val newModulePathString = artifactPath.safePathString

                if (cachedModulePathString == newModulePathString) {
                    log.info("Rewriting Ivy module '$fileName' detected. Paths match '$cachedModulePathString', the cached value will used.")
                } else {
                    log.warn(
                        """
                        Rewriting Ivy module '$fileName' detected. Paths do not match: '$cachedModulePathString' vs '$newModulePathString'.
                        The same artifact has been found in two different locations, the first one will be used: '$cachedModulePathString'.
                        """.trimIndent(),
                    )
                }
                return cachedIvyModule
            }
        } else if (cachedValue != null) {
            // If this happened, it means that this method is called somewhere with the wrong parameters.
            log.warn(
                """
                Unexpected flow.                
                Please file an issue attaching the content and exception message to: $GITHUB_REPOSITORY/issues/new

                File: $fileName
                Path: $artifactPath
                Cached value: $cachedValue
                """.trimIndent(),
            )
        }

        val ivyFile = providers.localPlatformArtifactsPath(rootProjectDirectory).get().resolve(fileName)

        val newIvyModule = block()
        IVY_MODULE_WRITE_LOCK.withLock {
            ivyFile
                .apply { parent.createDirectories() }
                .apply { deleteIfExists() }
                .createFile()
                .writeText(
                    XML {
                        indentString = "  "
                    }.encodeToString(newIvyModule),
                )
        }

        writtenIvyModules[fileName] = when (artifactPath) {
            null -> newIvyModule
            else -> Pair(newIvyModule, artifactPath)
        }

        return newIvyModule
    }

    /**
     * Creates an IntelliJ Platform dependency and excludes transitive dependencies provided by the current IntelliJ Platform.
     *
     * @param coordinates Dependency coordinates.
     * @param version Dependency version.
     * @param platformPath The path to the current IntelliJ Platform.
     */
    private fun createPlatformDependency(
        coordinates: Coordinates,
        version: String,
        platformPath: Path,
    ) = createDependency(coordinates, version).apply {
        val moduleDescriptors = providers.of(ModuleDescriptorsValueSource::class) {
            parameters {
                intellijPlatformPath = layout.dir(provider { platformPath.toFile() })
            }
        }.get()

        moduleDescriptors.forEach {
            exclude(it.groupId, it.artifactId)
        }
    }

    internal fun buildJetBrainsRuntimeVersion(
        version: String,
        runtimeVariant: String? = null,
        architecture: String? = null,
        operatingSystem: OperatingSystem = OperatingSystem.current(),
    ): String {
        val variant = runtimeVariant ?: "jcef"

        val (jdk, build) = version.split('b').also {
            require(it.size == 2) {
                "Incorrect JetBrains Runtime version: $version. Use [sdk]b[build] format, like: 21.0.3b446.1"
            }
        }

        val os = with(operatingSystem) {
            when {
                isWindows -> "windows"
                isMacOsX -> "osx"
                isLinux -> "linux"
                else -> throw GradleException("Unsupported operating system: $name")
            }
        }

        val osArch = (architecture ?: System.getProperty("os.arch")).lowercase()
        val arch = when {
            osArch in listOf("aarch64", "arm64") || osArch.contains("arm") || osArch.contains("aarch") -> "aarch64"
            osArch in listOf("x86_64", "amd64", "x64") -> "x64"
            operatingSystem.isWindows && System.getenv("ProgramFiles(x86)") != null -> "x64"
            else -> "x86"
        }

        return "jbr_$variant-$jdk-$os-$arch-b$build"
    }

    /**
     * Resolves the artifact path for the given [path] as it may accept different data types.
     *
     * @param path The local path of the artifact. Accepts either [String], [File], or [Directory].
     * @return The resolved artifact path.
     * @throws IllegalArgumentException if the [path] is not of supported types.
     */
    @Throws(IllegalArgumentException::class)
    internal fun resolveArtifactPath(path: Any) = resolvePath(path)
        .let { it.takeUnless { OperatingSystem.current().isMacOsX && it.extension == "app" } ?: it.resolve("Contents") }
        .takeIf { it.exists() && it.isDirectory() }
        .let { requireNotNull(it) { "Specified localPath '$path' doesn't exist or is not a directory." } }

    /**
     * Resolves the provided path into [Path].
     *
     * The method accepts a local path and converts it into a standardized Path object.
     * The local path can be of type String, File, Path, or Directory.
     *
     * @param path The local path to be resolved. Accepts either [String], [File], or [Directory].
     * @throws IllegalArgumentException
     */
    @Throws(IllegalArgumentException::class)
    internal fun resolvePath(path: Any) = when (path) {
        is String -> path
        is File -> path.toPath().safePathString
        is Directory -> path.asPath.safePathString
        is Path -> path.safePathString
        else -> throw IllegalArgumentException("Invalid argument type: '${path.javaClass}'. Supported types: String, File, Path, or Directory.")
    }.let { Path(it) }

    //</editor-fold>
}

internal typealias DependencyAction = (Dependency.() -> Unit)
