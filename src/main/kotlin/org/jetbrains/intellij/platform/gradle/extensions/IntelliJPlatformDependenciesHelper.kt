// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import com.jetbrains.plugin.structure.ide.createIde
import com.jetbrains.plugin.structure.ide.layout.MissingLayoutFileMode.SKIP_SILENTLY
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import com.jetbrains.plugin.structure.intellij.plugin.module.IdeModule
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XML
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.initialization.resolve.RulesMode
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes.ArtifactType
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Dependencies
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.Constants.Locations.GITHUB_REPOSITORY
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.providers.AndroidStudioDownloadLinkValueSource
import org.jetbrains.intellij.platform.gradle.providers.JavaRuntimeMetadataValueSource
import org.jetbrains.intellij.platform.gradle.providers.ModuleDescriptorsValueSource
import org.jetbrains.intellij.platform.gradle.providers.ProductReleasesValueSource
import org.jetbrains.intellij.platform.gradle.resolvers.path.resolveJavaRuntimeDirectory
import org.jetbrains.intellij.platform.gradle.resolvers.path.resolveJavaRuntimeExecutable
import org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask
import org.jetbrains.intellij.platform.gradle.tasks.SignPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.TestIdeUiTask
import org.jetbrains.intellij.platform.gradle.utils.*
import java.io.File
import java.io.FileReader
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

/**
 * Helper class for managing dependencies on the IntelliJ Platform in Gradle projects.
 *
 * @param configurations The Gradle [ConfigurationContainer] to manage configurations.
 * @param dependencies The Gradle [DependencyHandler] to manage dependencies.
 * @param layout The Gradle [ProjectLayout] to manage layout providers.
 * @param objects The Gradle [ObjectFactory] used for creating objects.
 * @param providers The Gradle [ProviderFactory] used for creating providers.
 * @param rootProjectDirectory The root directory of the Gradle project.
 */
class IntelliJPlatformDependenciesHelper(
    private val configurations: ConfigurationContainer,
    private val dependencies: DependencyHandler,
    private val layout: ProjectLayout,
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
    private val resources: ResourceHandler,
    private val rootProjectDirectory: Path,
    private val metadataRulesModeProvider: Provider<RulesMode>,
) {

    private val log = Logger(javaClass)
    private val pluginManager = IdePluginManager.createManager()

    /**
     * Key is Ivy module XML filename.
     * Value is either:
     * 1. The Ivy Module, representing the contents of XML file.
     *    In the case if we didn't have a path for the module.
     * 2. Otherwise, it is a pair of:
     *    - The Ivy Module, representing the contents of XML file.
     *    - The path from which this module was created.
     *
     * Since we store Ivy XML files into the same directory, but create them from completely unrelated paths.
     * This structure is being used to validate that we don't try to create duplicate XML files with the same name from
     * different paths (which would either a bug in this plugin, or duplicated dependency on the file system).
     *
     * [ConcurrentHashMap] has been used because Gradle is multithreaded.
     * It won't provide absolute thread safety, but is good enough for the task, considering what this is for.
     *
     * @see writeIvyModule
     */
    private val writtenIvyModules = ConcurrentHashMap<String, Any>()

    private val baseType = objects.property<IntelliJPlatformType>()
    private val baseVersion = objects.property<String>()
    private val baseUseInstaller = objects.property<Boolean>()

    /**
     * Helper function for accessing [ProviderFactory.provider] without exposing the whole [ProviderFactory].
     */
    internal inline fun <reified T : Any> provider(crossinline value: () -> T) = providers.provider {
        value()
    }

    /**
     * Helper function for creating cached [ProviderFactory.provider].
     */
    internal inline fun <reified T> cachedProvider(crossinline value: () -> T) =
        objects
            .property<T>()
            .value(providers.provider { value() })
            .apply {
                disallowChanges()
                finalizeValueOnRead()
            }

    /**
     * Helper function for creating cached list [ProviderFactory.provider].
     */
    internal inline fun <reified T> cachedListProvider(crossinline value: () -> List<T>) =
        objects
            .listProperty<T>()
            .value(providers.provider { value() })
            .apply {
                disallowChanges()
                finalizeValueOnRead()
            }

    //<editor-fold desc="Configuration Accessors">

    /**
     * Retrieves the [Configurations.INTELLIJ_PLATFORM_DEPENDENCY] configuration to access information about the main IntelliJ Platform added to the project.
     * Used with [productInfo] and [platformPath] accessors.
     */
    private val intelliJPlatformConfiguration by lazy {
        configurations[Configurations.INTELLIJ_PLATFORM_DEPENDENCY].asLenient
    }

    //</editor-fold>

    //<editor-fold desc="Metadata Accessors">

    /**
     * Provides access to the current IntelliJ Platform path.
     */
    internal val platformPath by lazy {
        intelliJPlatformConfiguration.platformPath()
    }

    /**
     * Provides access to the [ProductInfo] of the current IntelliJ Platform.
     */
    internal val productInfo by lazy {
        platformPath.productInfo()
    }

    internal val ide by lazy {
        createIde {
            missingLayoutFileMode = SKIP_SILENTLY
            path = platformPath
        }
    }

    //</editor-fold>

    //<editor-fold desc="Helper Methods">

    /**
     * Adds a dependency on a Jar bundled within the current IntelliJ Platform.
     *
     * @param pathProvider Path to the bundled Jar file, like `lib/testFramework.jar`.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addBundledLibrary(
        pathProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(cachedProvider {
        val path = pathProvider.orNull
        requireNotNull(path) { "The `intellijPlatform.bundledLibrary` dependency helper was called with no `path` value provided." }

        dependencies.createBundledLibrary(path).apply(action)
    }).also {
        log.warn(
            """
            Do not use `bundledLibrary()` in production, as direct access to the IntelliJ Platform libraries is not recommended.
            
            It should only be used as a workaround in case the IntelliJ Platform Gradle Plugin is not aligned with the latest IntelliJ Platform classpath changes.
            """.trimIndent()
        )
    }

    /**
     * A base method for adding a dependency on IntelliJ Platform.
     *
     * @param typeProvider The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param versionProvider The provider for the version of the IntelliJ Platform dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action An optional action to be performed on the created dependency.
     * @throws GradleException
     */
    @Throws(GradleException::class)
    internal fun addIntelliJPlatformDependency(
        typeProvider: Provider<*>,
        versionProvider: Provider<String>,
        useInstallerProvider: Provider<Boolean>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY_ARCHIVE,
        fallbackToBase: Boolean = false,
        action: DependencyAction = {},
    ) {
        val finalTypeProvider = with(typeProvider.map { it.toIntelliJPlatformType() }) {
            when (fallbackToBase) {
                true -> orElse(baseType)
                false -> also { baseType = this }
            }
        }
        val finalVersionProvider = with(versionProvider) {
            when (fallbackToBase) {
                true -> orElse(baseVersion)
                false -> also { baseVersion = this }
            }
        }
        val finalUseInstallerProvider = with(useInstallerProvider) {
            when (fallbackToBase) {
                true -> orElse(baseUseInstaller)
                false -> also { baseUseInstaller = this }
            }
        }

        configurations[configurationName].dependencies.addLater(cachedProvider {
            val type = finalTypeProvider.orNull
            val version = finalVersionProvider.orNull
            requireNotNull(type) { "The IntelliJ Platform dependency helper was called with no `type` value provided." }
            requireNotNull(version) { "The IntelliJ Platform dependency helper was called with no `version` value provided." }

            when (type) {
                IntelliJPlatformType.AndroidStudio -> dependencies.createAndroidStudio(version)
                else -> when (finalUseInstallerProvider.orNull ?: true) {
                    true -> dependencies.createIntelliJPlatformInstaller(type, version)
                    false -> dependencies.createIntelliJPlatform(type, version)
                }
            }.apply(action)
        })
    }

    /**
     * A base method for adding a dependency on IntelliJ Platform.
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
    ) = configurations[configurationName].dependencies.addAllLater(cachedListProvider {
        notationsProvider.get()
            .map { it.parseIdeNotation() }
            .map { (type, version) ->
                when (type) {
                    IntelliJPlatformType.AndroidStudio -> dependencies.createAndroidStudio(version)
                    else -> dependencies.createIntelliJPlatformInstaller(type, version)
                }.apply(action)
            }
    })

    /**
     * A base method for adding a dependency on a local IntelliJ Platform instance.
     *
     * @param localPathProvider The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action An optional action to be performed on the created dependency.
     * @throws GradleException
     */
    @Throws(GradleException::class)
    internal fun addIntelliJPlatformLocalDependency(
        localPathProvider: Provider<*>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_LOCAL,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(cachedProvider {
        val localPath = localPathProvider.orNull ?: return@cachedProvider null

        resolveArtifactPath(localPath)
            .let { dependencies.createIntelliJPlatformLocal(it) }
            .apply(action)
    })

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
    ) = configurations[configurationName].dependencies.addAllLater(cachedListProvider {
        val plugins = pluginsProvider.orNull
        requireNotNull(plugins) { "The `intellijPlatform.plugins` dependency helper was called with no `plugins` value provided." }

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
            dependencies.createIntelliJPlatformPlugin(id, version, group).apply(action)
        }
    })

    /**
     * A base method for adding a dependency on an IntelliJ Platform bundled plugin.
     *
     * @param bundledPluginsProvider The provider of the list containing bundled plugin identifiers.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addIntelliJPlatformBundledPluginDependencies(
        bundledPluginsProvider: Provider<List<String>>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(cachedListProvider {
        val bundledPlugins = bundledPluginsProvider.orNull
        requireNotNull(bundledPlugins) { "The `intellijPlatform.bundledPlugins` dependency helper was called with no `bundledPlugins` value provided." }

        bundledPlugins
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { dependencies.createIntelliJPlatformBundledPlugin(it) }
            .onEach(action)
    })

    /**
     * A base method for adding a dependency on an IntelliJ Platform bundled module.
     *
     * @param bundledModulesProvider The provider of the list containing bundled module identifiers.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addIntelliJPlatformBundledModuleDependencies(
        bundledModulesProvider: Provider<List<String>>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_BUNDLED_MODULES,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(cachedListProvider {
        val bundledModules = bundledModulesProvider.orNull
        requireNotNull(bundledModules) { "The `intellijPlatform.bundledModules` dependency helper was called with no `bundledModules` value provided." }

        bundledModules
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { dependencies.createIntelliJPlatformBundledModule(it) }
            .onEach(action)
    })

    /**
     * A base method for adding a dependency on a local plugin for IntelliJ Platform.
     *
     * @param localPathProvider The provider of the path to the local plugin.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addIntelliJPlatformLocalPluginDependency(
        localPathProvider: Provider<*>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(cachedProvider {
        val localPath = localPathProvider.orNull
        requireNotNull(localPath) { "The `intellijPlatform.localPlugin` dependency helper was called with no `localPath` value provided." }

        dependencies.createIntelliJPlatformLocalPlugin(localPath).apply(action)
    })

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
     * A base method for adding a project dependency on a module to be merged into the main plugin Jar archive by [ComposedJarTask].
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
    ) = configurations[configurationName].dependencies.addLater(cachedProvider {
        val version = versionProvider.orNull
        requireNotNull(version) { "The `intellijPlatform.javaCompiler` dependency helper was called with no `version` value provided." }

        createJavaCompiler(version).apply(action)
    })

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
    ) = configurations[configurationName].dependencies.addLater(cachedProvider {
        val explicitVersion = explicitVersionProvider.orNull
        requireNotNull(explicitVersion) { "The `intellijPlatform.jetbrainsRuntime`/`intellijPlatform.jetbrainsRuntimeExplicit` dependency helper was called with no `version` value provided." }

        createJetBrainsRuntime(explicitVersion).apply(action)
    })

    /**
     * A base method for adding a dependency on JetBrains Runtime.
     *
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addJetBrainsRuntimeObtainedDependency(
        configurationName: String = Configurations.JETBRAINS_RUNTIME_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(obtainJetBrainsRuntimeVersion().map { version ->
        createJetBrainsRuntime(version).apply(action)
    })

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
    ) = configurations[configurationName].dependencies.addLater(cachedProvider {
        val localPath = localPathProvider.orNull
        requireNotNull(localPath) { "The `intellijPlatform.jetbrainsRuntimeLocal` dependency helper was called with no `localPath` value provided." }

        val artifactPath = resolvePath(localPath)
            .takeIf { it.exists() }
            ?.resolveJavaRuntimeDirectory()
            .let { requireNotNull(it) { "Specified localPath '$localPath' doesn't exist." } }

        dependencies.createJetBrainsRuntimeLocal(artifactPath).apply(action)
    })

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
    ) = configurations[configurationName].dependencies.addLater(cachedProvider {
        val version = versionProvider.orNull
        requireNotNull(version) { "The `intellijPlatform.pluginVerifier` dependency helper was called with no `version` value provided." }

        createPluginVerifier(version).apply(action)
    })

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
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addTestFrameworkDependency(
        type: TestFrameworkType,
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(cachedListProvider {
        when (type) {
            TestFrameworkType.Bundled -> type.coordinates.map {
                dependencies.createBundledLibrary(it.artifactId)
            }

            else -> {
                val version = versionProvider.orNull
                requireNotNull(version) { "The `intellijPlatform.testFramework` dependency helper was called with no `version` value provided." }

                type.coordinates.map {
                    dependencies.createPlatformDependency(it, version)
                }
            }
        }.onEach(action)
    })

    /**
     * Adds a dependency on the IntelliJ Platform dependency.
     *
     * This dependency belongs to IntelliJ Platform repositories.
     *
     * @param coordinates The coordinates of the IntelliJ Platform dependency.
     * @param versionProvider The version of the IntelliJ Platform dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addPlatformDependency(
        coordinates: Coordinates,
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(cachedProvider {
        val version = versionProvider.get()
        requireNotNull(version) { "The `intellijPlatform.platformDependency`/`intellijPlatform.testPlatformDependency` dependency helper was called with no `version` value provided." }

        dependencies.createPlatformDependency(coordinates, version).apply(action)
    })

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
    ) = configurations[configurationName].dependencies.addLater(cachedProvider {
        val version = versionProvider.orNull
        requireNotNull(version) { "The `intellijPlatform.zipSigner` dependency helper was called with no `version` value provided." }

        createMarketplaceZipSigner(version).apply(action)
    })

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
    ) = configurations[configurationName].dependencies.addLater(cachedProvider {
        val version = versionProvider.orNull
        requireNotNull(version) { "The `intellijPlatform.robotServerPlugin` dependency helper was called with no `version` value provided." }

        createRobotServerPlugin(version).apply(action)
    })

    internal fun createProductReleasesValueSource(configure: ProductReleasesValueSource.FilterParameters.() -> Unit) =
        providers.of(ProductReleasesValueSource::class.java) {
            parameters.jetbrainsIdesUrl = providers[GradleProperties.ProductsReleasesJetBrainsIdesUrl]
            parameters.androidStudioUrl = providers[GradleProperties.ProductsReleasesAndroidStudioUrl]
            parameters.jetbrainsIdes = resources.resolveUrl(parameters.jetbrainsIdesUrl)
            parameters.androidStudio = resources.resolveUrl(parameters.androidStudioUrl)

            parameters(configure)
        }

    //</editor-fold>

    //<editor-fold desc="DependencyHelper Extensions">

    /**
     * Creates Android Studio dependency.
     *
     * @param version Android Studio version.
     */
    private fun DependencyHandler.createAndroidStudio(version: String): Dependency {
        val type = IntelliJPlatformType.AndroidStudio
        val downloadLink = providers.of(AndroidStudioDownloadLinkValueSource::class) {
            parameters {
                androidStudioUrl = providers[GradleProperties.ProductsReleasesAndroidStudioUrl]
                androidStudioVersion = version
            }
        }.orNull

        requireNotNull(downloadLink) { "Couldn't resolve Android Studio download URL for version: '$version'" }
        requireNotNull(type.installer) { "Specified type '$type' has no artifact coordinates available." }

        val (classifier, extension) = downloadLink.substringAfter("$version-").split(".", limit = 2)

        return create(
            group = type.installer.groupId,
            name = type.installer.artifactId,
            classifier = classifier,
            ext = extension,
            version = version,
        )
    }

    /**
     * Creates a [Dependency] using a Jar file resolved in [platformPath] with [path].
     *
     * @param path Relative path to the library, like: `lib/testFramework.jar`.
     */
    private fun DependencyHandler.createBundledLibrary(path: String) = create(
        objects.fileCollection().from(platformPath.map {
            it.resolve(path)
        })
    )

    /**
     * Creates IntelliJ Platform dependency based on the provided version and type.
     *
     * @param version IntelliJ Platform version.
     * @param type IntelliJ Platform type.
     */
    private fun DependencyHandler.createIntelliJPlatform(type: IntelliJPlatformType, version: String): Dependency {
        requireNotNull(type.maven) { "Specified type '$type' has no artifact coordinates available." }

        return create(
            group = type.maven.groupId,
            name = type.maven.artifactId,
            version = version,
        )
    }

    /**
     * Creates IntelliJ Platform dependency based on the provided version and type.
     *
     * @param version IntelliJ Platform version.
     * @param type IntelliJ Platform type.
     */
    private fun DependencyHandler.createIntelliJPlatformInstaller(type: IntelliJPlatformType, version: String): Dependency {
        requireNotNull(type.installer) { "Specified type '$type' has no artifact coordinates available." }

        val (extension, classifier) = with(OperatingSystem.current()) {
            val arch = System.getProperty("os.arch").takeIf { it == "aarch64" }
            when {
                isWindows -> ArtifactType.ZIP to "win"
                isLinux -> ArtifactType.TAR_GZ to arch
                isMacOsX -> ArtifactType.DMG to arch
                else -> throw GradleException("Unsupported operating system: $name")
            }
        }.let { (type, classifier) -> type.toString() to classifier }

        return create(
            group = type.installer.groupId,
            name = type.installer.artifactId,
            version = version,
            ext = extension,
            classifier = classifier,
        )
    }

    /**
     * Creates a dependency on a local IntelliJ Platform instance.
     *
     * @param artifactPath Path to the local IntelliJ Platform.
     */
    private fun DependencyHandler.createIntelliJPlatformLocal(artifactPath: Path): Dependency {
        val localProductInfo = artifactPath.productInfo()
        localProductInfo.validateSupportedVersion()

        val type = localProductInfo.productCode.toIntelliJPlatformType()
        // It is crucial to use the IDE type + build number to the version.
        // Because if UI & IC are used by different submodules in the same build, they might rewrite each other's Ivy
        // XML files, which might have different optional transitive dependencies defined due to IC having fewer plugins.
        val version = localProductInfo.getFullVersion()

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

        return create(
            group = Dependencies.LOCAL_IDE_GROUP,
            name = type.code,
            version = version,
        )
    }

    /**
     * Creates a dependency for an IntelliJ Platform plugin.
     *
     * @param pluginId The ID of the plugin.
     * @param version The version of the plugin.
     * @param group The channel of the plugin. Can be null or empty for the default channel.
     */
    private fun DependencyHandler.createIntelliJPlatformPlugin(pluginId: String, version: String, group: String): Dependency {
        val groupId = group.substringBefore('@').ifEmpty { Dependencies.MARKETPLACE_GROUP }
        val channel = group.substringAfter('@', "")

        return create(
            group = "$channel.$groupId".trim('.'),
            name = pluginId.trim(),
            version = version,
        )
    }

    /**
     * Creates a dependency for an IntelliJ platform bundled plugin.
     *
     * @param id The ID of the bundled plugin.
     */
    private fun DependencyHandler.createIntelliJPlatformBundledPlugin(id: String): Dependency {
        val plugin = ide.findPluginById(id)
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

        val artifactPath = requireNotNull(plugin.originalFile)
        // It is crucial to use the IDE type + build number to the version.
        // Because if UI & IC are used by different submodules in the same build, they might rewrite each other's Ivy
        // XML files, which might have different optional transitive dependencies defined due to IC having fewer plugins.
        // Should be the same as [collectDependencies]
        val version = productInfo.getFullVersion()

        writeIvyModule(Dependencies.BUNDLED_PLUGIN_GROUP, id, version, artifactPath) {
            IvyModule(
                info = IvyModule.Info(
                    organisation = Dependencies.BUNDLED_PLUGIN_GROUP,
                    module = id,
                    revision = version,
                ),
                publications = artifactPath.toIvyArtifacts(metadataRulesModeProvider, platformPath),
                dependencies = plugin.collectDependencies(),
            )
        }

        return create(
            group = Dependencies.BUNDLED_PLUGIN_GROUP,
            name = id,
            version = version,
        )
    }

    /**
     * Creates a dependency for an IntelliJ platform bundled module.
     *
     * @param id The ID of the bundled module.
     */
    private fun DependencyHandler.createIntelliJPlatformBundledModule(id: String): Dependency {
        val bundledModule = productInfo.layout
            .find { layout -> layout.name == id }
            .let { requireNotNull(it) { "Specified bundledModule '$id' doesn't exist." } }

        val (group, name, version) = writeBundledModuleDependency(id, bundledModule.classPath)
        return create(group, name, version)
    }

    /**
     * Collects all dependencies on plugins or modules of the current [IdePlugin].
     * The [alreadyProcessedOrProcessing] parameter is a list of already traversed entities, used to avoid circular dependencies when walking recursively.
     *
     * @param alreadyProcessedOrProcessing IDs of already traversed plugins or modules.
     */
    private fun IdePlugin.collectDependencies(alreadyProcessedOrProcessing: List<String> = emptyList()): List<IvyModule.Dependency> {
        // It is crucial to use the IDE type + build number to the version.
        // Because if UI & IC are used by different submodules in the same build, they might rewrite each other's Ivy
        // XML files, which might have different optional transitive dependencies defined due to IC having fewer plugins.
        // Should be the same as [createIntelliJPlatformBundledPlugin]
        val id = requireNotNull(pluginId)
        val version = ide.version.toString()

        return dependencies
            .mapNotNull { ide.findPluginById(it.id) }
            .map {
                val name = requireNotNull(it.pluginId)
                val group = when {
                    it is IdeModule -> Dependencies.BUNDLED_MODULE_GROUP
                    else -> Dependencies.BUNDLED_PLUGIN_GROUP
                }
                val publications = when {
                    it is IdeModule -> it.classpath.flatMap { path ->
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
                            dependencies = it.collectDependencies(alreadyProcessedOrProcessing + id),
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
     * @return A [Triple] containing dependency group, name, and version
     */
    private fun writeBundledModuleDependency(name: String, classPath: List<String>): Triple<String, String, String> {
        val group = Dependencies.BUNDLED_MODULE_GROUP
        // It is crucial to use the IDE type + build number to the version.
        // Because if UI & IC are used by different submodules in the same build, they might rewrite each other's Ivy
        // XML files, which might have different optional transitive dependencies defined due to IC having fewer plugins.
        val version = productInfo.getFullVersion()
        val platformPath = platformPath
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
     */
    private fun DependencyHandler.createIntelliJPlatformLocalPlugin(localPath: Any): Dependency {
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
        val version = productInfo.getFullVersion() + "-" + (plugin.pluginVersion ?: "0.0.0")
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

        return create(
            group = Dependencies.LOCAL_PLUGIN_GROUP,
            name = name,
            version = version,
        )
    }

    /**
     * Creates a dependency on a local JetBrains Runtime instance.
     *
     * @param artifactPath Path to the local JetBrains Runtime.
     */
    private fun DependencyHandler.createJetBrainsRuntimeLocal(artifactPath: Path): Dependency {
        val runtimeMetadata = providers.of(JavaRuntimeMetadataValueSource::class) {
            parameters {
                executable = layout.file(provider {
                    artifactPath.resolveJavaRuntimeExecutable().toFile()
                })
            }
        }.get()

        // E.g.: JBR-21.0.4+13-509.17-jcef
        val javaVendorVersion = runtimeMetadata["java.vendor.version"]
        requireNotNull(javaVendorVersion)

        // E.g.: 21.0.4
        val javaVersion = runtimeMetadata["java.version"]
        requireNotNull(javaVersion)

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

        return create(
            group = Dependencies.LOCAL_JETBRAINS_RUNTIME_GROUP,
            name = name,
            version = version,
        )
    }

    /**
     * Creates Java Compiler dependency.
     *
     * @param version Java Compiler dependency version
     */
    internal fun createJavaCompiler(version: String = Constraints.CLOSEST_VERSION) =
        dependencies.createDependency(
            coordinates = Coordinates("com.jetbrains.intellij.java", "java-compiler-ant-tasks"),
            version = version,
        )

    /**
     * Creates JetBrains Runtime (JBR) dependency.
     *
     * @param version JetBrains Runtime (JBR) dependency version
     */
    internal fun createJetBrainsRuntime(version: String) =
        dependencies.create(
            group = "com.jetbrains",
            name = "jbr",
            version = version,
            ext = "tar.gz",
        )

    /**
     * Creates IntelliJ Plugin Verifier CLI tool dependency.
     *
     * @param version IntelliJ Plugin Verifier CLI tool dependency version
     */
    internal fun createPluginVerifier(version: String = Constraints.LATEST_VERSION) =
        dependencies.createDependency(
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
        dependencies.createDependency(
            coordinates = Coordinates("org.jetbrains", "marketplace-zip-signer"),
            version = version,
            classifier = "cli",
            extension = "jar",
        )

    /**
     * Adds a dependency on a Robot Server Plugin required for signing plugin with [TestIdeUiTask].
     *
     * @param version Robot Server Plugin version.
     */
    internal fun createRobotServerPlugin(version: String) =
        dependencies.createDependency(
            coordinates = Coordinates("com.intellij.remoterobot", "robot-server-plugin"),
            version = version,
            extension = "zip",
        )

    /**
     * Creates a [Provider] that holds a JetBrains Runtime version obtained using the currently used IntelliJ Platform.
     *
     * @param platformPathProvider The path to the IntelliJ Platform to be used for resolving JetBrains Runtime version.
     */
    internal fun obtainJetBrainsRuntimeVersion(platformPathProvider: Provider<Path> = provider { platformPath }) = cachedProvider {
        val dependencies = runCatching {
            platformPathProvider.get().resolve("dependencies.txt").takeIf { it.exists() }
        }.getOrNull() ?: return@cachedProvider null

        val version = FileReader(dependencies.toFile()).use { reader ->
            with(Properties()) {
                load(reader)
                getProperty("runtimeBuild") ?: getProperty("jdkBuild")
            }
        } ?: return@cachedProvider null

        buildJetBrainsRuntimeVersion(version)
    }

    /**
     * Creates a dependency.
     *
     * @param coordinates Dependency coordinates.
     * @param version Dependency version.
     * @param classifier Optional dependency classifier.
     * @param extension Optional dependency extension.
     */
    private fun DependencyHandler.createDependency(
        coordinates: Coordinates,
        version: String,
        classifier: String? = null,
        extension: String? = null,
    ) = create(
        group = coordinates.groupId,
        name = coordinates.artifactId,
        classifier = classifier,
        ext = extension,
    ).apply {
        when (version) {
            Constraints.PLATFORM_VERSION ->
                version {
                    prefer(baseVersion.get())
                }

            Constraints.CLOSEST_VERSION ->
                version {
                    val buildNumber by lazy {
                        runCatching {
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
                    prefer(version)
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
    private fun writeIvyModule(group: String, artifact: String, version: String, artifactPath: Path?, block: () -> IvyModule): IvyModule {
        val fileName = "$group-$artifact-$version.xml"

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
                val cachedModulePathString = cachedModulePath.absolute().normalize().invariantSeparatorsPathString
                val newModulePathString = artifactPath.absolute().normalize().invariantSeparatorsPathString

                if (cachedModulePathString == newModulePathString) {
                    log.info("Rewriting Ivy module '$fileName' detected. Paths match '$cachedModulePathString', the cached value will used.")
                } else {
                    log.warn(
                        """
                        Rewriting Ivy module '$fileName' detected. Paths do not match: '$cachedModulePathString' vs '$newModulePathString'.
                        The same artifact has been found in two different locations, the first one will be used: '$cachedModulePathString'.
                        """.trimIndent()
                    )
                }
                return cachedIvyModule
            }
        } else if (cachedValue != null) {
            // If this happened, it means that this method is called somewhere with wrong parameters.
            log.warn(
                """
                Unexpected flow.                
                Please file an issue attaching the content and exception message to: $GITHUB_REPOSITORY/issues/new
                
                File: $fileName
                Path: $artifactPath
                Cached value: $cachedValue
                """.trimIndent()
            )
        }

        val ivyFile = providers
            .localPlatformArtifactsPath(rootProjectDirectory)
            .resolve(fileName)

        val newIvyModule = block()
        ivyFile
            .apply { parent.createDirectories() }
            .apply { deleteIfExists() }
            .createFile()
            .writeText(XML {
                indentString = "  "
            }.encodeToString(newIvyModule))

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
     */
    private fun DependencyHandler.createPlatformDependency(
        coordinates: Coordinates,
        version: String,
    ) = createDependency(coordinates, version).apply {
        val moduleDescriptors = providers.of(ModuleDescriptorsValueSource::class) {
            parameters {
                intellijPlatformPath = layout.dir(provider { platformPath.toFile() })
            }
        }

        moduleDescriptors.get().forEach {
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

        val arch = when (architecture ?: System.getProperty("os.arch")) {
            "aarch64", "arm64" -> "aarch64"
            "x86_64", "amd64" -> "x64"
            else -> when {
                operatingSystem.isWindows && System.getenv("ProgramFiles(x86)") != null -> "x64"
                else -> "x86"
            }
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
     * The local path can be of type String, File, or Directory.
     *
     * @param path The local path to be resolved. Accepts either [String], [File], or [Directory].
     * @throws IllegalArgumentException
     */
    @Throws(IllegalArgumentException::class)
    internal fun resolvePath(path: Any) = when (path) {
        is String -> path
        is File -> path.absolutePath
        is Directory -> path.asPath.pathString
        else -> throw IllegalArgumentException("Invalid argument type: '${path.javaClass}'. Supported types: String, File, or Directory.")
    }.let { Path(it) }

    //</editor-fold>
}

internal typealias DependencyAction = (Dependency.() -> Unit)
