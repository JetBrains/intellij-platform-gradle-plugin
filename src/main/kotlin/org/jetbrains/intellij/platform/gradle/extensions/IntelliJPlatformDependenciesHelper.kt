// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XML
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes.ArtifactType
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Dependencies
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
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
import kotlin.io.path.*

/**
 * Helper class for managing dependencies on the IntelliJ Platform in Gradle projects.
 *
 * @param configurations The Gradle [ConfigurationContainer] to manage configurations.
 * @param dependencies The Gradle [DependencyHandler] to manage dependencies.
 * @param layout The Gradle [ProjectLayout] to manage layout providers.
 * @param objects The Gradle [ObjectFactory] used for creating objects.
 * @param providers The Gradle [ProviderFactory] used for creating providers.
 * @param resources The Gradle [ResourceHandler] used for managing resources.
 * @param rootProjectDirectory The root directory of the Gradle project.
 */
class IntelliJPlatformDependenciesHelper(
    private val repositories: RepositoryHandler,
    private val configurations: ConfigurationContainer,
    private val dependencies: DependencyHandler,
    private val layout: ProjectLayout,
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
    private val resources: ResourceHandler,
    private val rootProjectDirectory: Path,
) {

    private val log = Logger(javaClass)
    private val pluginManager = IdePluginManager.createManager()
    private val writtenIvyModules = mutableSetOf<String>()

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
    private val intelliJPlatformConfiguration
        get() = configurations[Configurations.INTELLIJ_PLATFORM_DEPENDENCY].asLenient

    //</editor-fold>

    //<editor-fold desc="Metadata Accessors">

    /**
     * Provides access to the current IntelliJ Platform path.
     */
    internal val platformPath
        get() = provider { intelliJPlatformConfiguration.platformPath() }

    /**
     * Provides access to the [ProductInfo] of the current IntelliJ Platform.
     */
    internal val productInfo
        get() = platformPath.map { it.productInfo() }

    private val bundledPlugins by lazy {
        platformPath.get()
            .resolve("plugins")
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .mapNotNull { path ->
                // TODO: try not to parse all plugins at once
                pluginManager.safelyCreatePlugin(path)
                    .onFailure { log.warn(it.message.orEmpty()) }
                    .getOrNull()
            }
            .associateBy { requireNotNull(it.pluginId) }
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
        dependencyConfigurationName: String = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
        configurationName: String = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES,
        action: DependencyAction = {},
    ) = configurations.addAllLater(cachedListProvider {
        notationsProvider.get()
            .map { it.parseIdeNotation() }
            .map { (type, version) ->
                val dependency = when (type) {
                    IntelliJPlatformType.AndroidStudio -> dependencies.createAndroidStudio(version)
                    else -> dependencies.createIntelliJPlatformInstaller(type, version)
                }.apply(action)

                val dependencyConfigurationNameWithNotation = "${dependencyConfigurationName}_${type}_${version}"
                val configurationNameWithNotation = "${configurationName}_${type}_${version}"

                val dependencyConfiguration = configurations.findByName(dependencyConfigurationNameWithNotation)
                    ?: configurations.create(dependencyConfigurationNameWithNotation) {
                        dependencies.add(dependency)
                    }

                configurations.findByName(configurationNameWithNotation)
                    ?: configurations.create(configurationNameWithNotation) {
                        attributes {
                            attribute(Attributes.extracted, true)
                        }
                        extendsFrom(dependencyConfiguration)
                    }
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

        dependencies.createDependency(
            coordinates = Coordinates("com.jetbrains.intellij.java", "java-compiler-ant-tasks"),
            version = version,
        ).apply(action)
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

        dependencies.createJetBrainsRuntime(explicitVersion).apply(action)
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
    ) = addJetBrainsRuntimeDependency(
        provider {
            val version = platformPath.get().resolve("dependencies.txt").takeIf { it.exists() }?.let {
                FileReader(it.toFile()).use { reader ->
                    with(Properties()) {
                        load(reader)
                        getProperty("runtimeBuild") ?: getProperty("jdkBuild")
                    }
                }
            } ?: throw GradleException("Could not obtain JetBrains Runtime version with the current IntelliJ Platform.")

            buildJetBrainsRuntimeVersion(version)
        },
        configurationName,
        action,
    )

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

        dependencies.createPluginVerifier(version).apply(action)
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

        dependencies.createMarketplaceZipSigner(version).apply(action)
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

        dependencies.createRobotServerPlugin(version).apply(action)
    })

    internal fun createProductReleasesValueSource(configure: ProductReleasesValueSource.FilterParameters.() -> Unit) =
        providers.of(ProductReleasesValueSource::class.java) {
            parameters.jetbrainsIdesUrl = providers[GradleProperties.ProductsReleasesJetBrainsIdesUrl]
            parameters.androidStudioUrl = providers[GradleProperties.ProductsReleasesAndroidStudioUrl]

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
        val version = localProductInfo.buildNumber

        writeIvyModule(Dependencies.LOCAL_IDE_GROUP, type.code, version) {
            IvyModule(
                info = IvyModule.Info(
                    organisation = Dependencies.LOCAL_IDE_GROUP,
                    module = type.code,
                    revision = version,
                ),
                publications = listOf(artifactPath.toIvyArtifact()),
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
        val plugin = bundledPlugins[id]

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
        val version = baseVersion.orElse(productInfo.map { it.version }).get()

        writeIvyModule(Dependencies.BUNDLED_PLUGIN_GROUP, id, version) {
            IvyModule(
                info = IvyModule.Info(
                    organisation = Dependencies.BUNDLED_PLUGIN_GROUP,
                    module = id,
                    revision = version,
                ),
                publications = artifactPath.toBundledPluginIvyArtifacts(),
                dependencies = plugin.collectBundledPluginDependencies(),
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
        val bundledModule = productInfo.get().layout
            .find { layout -> layout.name == id }
            .let { requireNotNull(it) { "Specified bundledModule '$id' doesn't exist." } }
        val platformPath = platformPath.get()
        val artifactPaths = bundledModule.classPath.flatMap { path ->
            platformPath.resolve(path).toBundledModuleIvyArtifacts()
        }
        val version = baseVersion.orElse(productInfo.map { it.version }).get()

        writeIvyModule(Dependencies.BUNDLED_MODULE_GROUP, id, version) {
            IvyModule(
                info = IvyModule.Info(
                    organisation = Dependencies.BUNDLED_MODULE_GROUP,
                    module = id,
                    revision = version,
                ),
                publications = artifactPaths,
            )
        }

        return create(
            group = Dependencies.BUNDLED_MODULE_GROUP,
            name = id,
            version = version,
        )
    }

    /**
     * Collects all dependencies on plugins or modules of the current [IdePlugin].
     * The [path] parameter is a list of already traversed entities, used to avoid circular dependencies when walking recursively.
     *
     * @param path IDs of already traversed plugins or modules.
     */
    private fun IdePlugin.collectBundledPluginDependencies(path: List<String> = emptyList()): List<IvyModule.Dependency> {
        val id = requireNotNull(pluginId)
        val dependencyIds = (dependencies.map { it.id } + optionalDescriptors.map { it.dependency.id } + modulesDescriptors.map { it.name } - id).toSet()
        val buildNumber by lazy { productInfo.get().buildNumber }
        val platformPath by lazy { platformPath.get() }

        val plugins = dependencyIds
            .mapNotNull { bundledPlugins[it] }
            .map { plugin ->
                val artifactPath = requireNotNull(plugin.originalFile)
                val group = Dependencies.BUNDLED_PLUGIN_GROUP
                val name = requireNotNull(plugin.pluginId)
                val version = requireNotNull(plugin.pluginVersion)

                writeIvyModule(group, name, version) {
                    IvyModule(
                        info = IvyModule.Info(group, name, version),
                        publications = artifactPath.toBundledPluginIvyArtifacts(),
                        dependencies = when {
                            id in path -> emptyList()
                            else -> plugin.collectBundledPluginDependencies(path + id)
                        },
                    )
                }

                IvyModule.Dependency(group, name, version)
            }

        val layoutItems = productInfo.get().layout
            .filter { layout -> layout.name in dependencyIds }
            .filter { layout -> layout.classPath.isNotEmpty() }

        val modules = dependencyIds
            .filterNot { bundledPlugins.containsKey(it) }
            .mapNotNull { layoutItems.find { layout -> layout.name == it } }
            .filterNot { it.classPath.isEmpty() }
            .map {
                val artifactPaths = it.classPath.flatMap { path ->
                    platformPath.resolve(path).toBundledModuleIvyArtifacts()
                }
                val group = Dependencies.BUNDLED_MODULE_GROUP
                val name = it.name
                val version = buildNumber

                writeIvyModule(group, name, version) {
                    IvyModule(
                        info = IvyModule.Info(group, name, version),
                        publications = artifactPaths,
                    )
                }

                IvyModule.Dependency(group, name, version)
            }

        return plugins + modules
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
                artifactPath.isDirectory() -> generateSequence(artifactPath) {
                    it.takeIf { it.resolve("lib").exists() } ?: it.listDirectoryEntries().singleOrNull()
                }.firstOrNull { it.resolve("lib").exists() } ?: throw GradleException("Could not resolve plugin directory: '$artifactPath'")

                else -> artifactPath
            }
            pluginManager.safelyCreatePlugin(pluginPath).getOrThrow()
        }

        val version = plugin.pluginVersion ?: "0.0.0"
        val name = plugin.pluginId ?: artifactPath.name

        writeIvyModule(Dependencies.LOCAL_PLUGIN_GROUP, name, version) {
            IvyModule(
                info = IvyModule.Info(
                    organisation = Dependencies.LOCAL_PLUGIN_GROUP,
                    module = name,
                    revision = version,
                ),
                publications = artifactPath.toLocalPluginIvyArtifacts(),
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

        val javaVendorVersion = runtimeMetadata["java.vendor.version"]
        requireNotNull(javaVendorVersion)

        val javaVersion = runtimeMetadata["java.version"]
        requireNotNull(javaVersion)

        val name = javaVendorVersion.substringBefore(javaVersion).trim('-')
        val version = javaVendorVersion.removePrefix(name).trim('-')

        writeIvyModule(Dependencies.LOCAL_JETBRAINS_RUNTIME_GROUP, name, version) {
            IvyModule(
                info = IvyModule.Info(
                    organisation = Dependencies.LOCAL_JETBRAINS_RUNTIME_GROUP,
                    module = name,
                    revision = version,
                ),
                publications = listOf(artifactPath.toIvyArtifact()),
            )
        }

        return create(
            group = Dependencies.LOCAL_JETBRAINS_RUNTIME_GROUP,
            name = name,
            version = version,
        )
    }

    /**
     * Creates JetBrains Runtime (JBR) dependency.
     */
    private fun DependencyHandler.createJetBrainsRuntime(version: String) = create(
        group = "com.jetbrains",
        name = "jbr",
        version = version,
        ext = "tar.gz",
    )

    /**
     * Creates IntelliJ Plugin Verifier CLI tool dependency.
     */
    private fun DependencyHandler.createPluginVerifier(version: String) = createDependency(
        coordinates = Coordinates("org.jetbrains.intellij.plugins", "verifier-cli"),
        version = version,
        classifier = "all",
        extension = "jar",
    )

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
        val buildNumber by lazy { productInfo.map { it.buildNumber.toVersion() }.get() }

        when (version) {
            Constraints.PLATFORM_VERSION ->
                version {
                    prefer(baseVersion.get())
                }

            Constraints.CLOSEST_VERSION ->
                version {
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
     */
    private fun writeIvyModule(group: String, artifact: String, version: String, block: () -> IvyModule) = apply {
        val fileName = "$group-$artifact-$version.xml"
        if (writtenIvyModules.contains(fileName)) {
            return@apply
        }

        val ivyFile = providers
            .localPlatformArtifactsPath(rootProjectDirectory)
            .resolve(fileName)

        ivyFile
            .apply { parent.createDirectories() }
            .apply { deleteIfExists() }
            .createFile()
            .writeText(XML {
                indentString = "  "
            }.encodeToString(block()))

        writtenIvyModules.add(fileName)
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
                intellijPlatformPath = layout.dir(platformPath.map { it.toFile() })
            }
        }

        moduleDescriptors.get().forEach {
            exclude(it.groupId, it.artifactId)
        }
    }

    /**
     * Adds a dependency on a Marketplace ZIP Signer required for signing plugin with [SignPluginTask].
     *
     * @param version Marketplace ZIP Signer version.
     */
    private fun DependencyHandler.createMarketplaceZipSigner(version: String) = createDependency(
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
    private fun DependencyHandler.createRobotServerPlugin(version: String) = createDependency(
        coordinates = Coordinates("com.intellij.remoterobot", "robot-server-plugin"),
        version = version,
        extension = "zip",
    )

    internal fun buildJetBrainsRuntimeVersion(
        version: String,
        runtimeVariant: String? = null,
        architecture: String? = null,
        operatingSystem: OperatingSystem = OperatingSystem.current(),
    ): String {
        val variant = runtimeVariant ?: "jcef"

        val (jdk, build) = version.split('b').also {
            assert(it.size == 1) {
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
