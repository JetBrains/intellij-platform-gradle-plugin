// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
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
import org.jetbrains.intellij.platform.gradle.Constants.JETBRAINS_MARKETPLACE_MAVEN_GROUP
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.providers.AndroidStudioDownloadLinkValueSource
import org.jetbrains.intellij.platform.gradle.providers.ModuleDescriptorsValueSource
import org.jetbrains.intellij.platform.gradle.providers.ProductReleasesValueSource
import org.jetbrains.intellij.platform.gradle.resolvers.version.ClosestVersionResolver
import org.jetbrains.intellij.platform.gradle.resolvers.version.LatestVersionResolver
import org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask
import org.jetbrains.intellij.platform.gradle.tasks.SignPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.TestIdeUiTask
import org.jetbrains.intellij.platform.gradle.utils.*
import java.io.File
import java.io.FileReader
import java.net.URL
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.math.absoluteValue


/**
 * Helper class for managing dependencies on the IntelliJ Platform in Gradle projects.
 *
 * @param configurations The Gradle `ConfigurationContainer` used for managing configurations.
 * @param dependencies The Gradle `DependencyHandler` used for managing dependencies.
 * @param layout The Gradle `ProjectLayout` used for managing project layout.
 * @param objects The Gradle `ObjectFactory` used for creating objects.
 * @param providers The Gradle `ProviderFactory` used for creating providers.
 * @param repositories The Gradle `RepositoryHandler` used for managing repositories.
 * @param resources The Gradle `ResourceHandler` used for managing resources.
 * @param rootProjectDirectory The root directory of the Gradle project.
 * @param settingsRepositories The Gradle `RepositoryHandler` used for retrieving repositories declared in settings.
 */
class IntelliJPlatformDependenciesHelper(
    private val configurations: ConfigurationContainer,
    private val dependencies: DependencyHandler,
    private val layout: ProjectLayout,
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
    private val repositories: RepositoryHandler,
    private val resources: ResourceHandler,
    private val rootProjectDirectory: Path,
    private val settingsRepositories: RepositoryHandler,
) {

    private val log = Logger(javaClass)

    private val baseType = objects.property<IntelliJPlatformType>()
    private val baseVersion = objects.property<String>()
    private val repositoryUrls
        get() = (repositories.urls() + settingsRepositories.urls())
            .map { URL(it) }
            .filterNot { it.protocol == "http" }

    /**
     * Helper function for accessing [ProviderFactory.provider] without exposing the whole [ProviderFactory].
     */
    internal inline fun <reified T : Any> provider(crossinline value: () -> T) = providers.provider {
        value()
    }

    //<editor-fold desc="Configuration Accessors">

    /**
     * Retrieves the [Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS_LIST] configuration to access information about plugins
     * bundled within the IntelliJ Platform added to the project.
     * Used with [bundledPlugins] accessor.
     */
    private val bundledPluginsListConfiguration
        get() = configurations[Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS_LIST].asLenient

    /**
     * Retrieves the [Configurations.INTELLIJ_PLATFORM] configuration to access information about the main IntelliJ Platform added to the project.
     * Used with [productInfo] and [platformPath] accessors.
     */
    private val intelliJPlatformConfiguration
        get() = configurations[Configurations.INTELLIJ_PLATFORM].asLenient

    //</editor-fold>

    //<editor-fold desc="Metadata Accessors">

    internal val bundledPlugins
        get() = provider { bundledPluginsListConfiguration.bundledPlugins() }

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

    //</editor-fold>

    //<editor-fold desc="Helper Methods">

    /**
     * Adds a dependency on a Jar bundled within the current IntelliJ Platform.
     *
     * @param pathProvider Path to the bundled Jar file, like `lib/testFramework.jar`
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addBundledLibrary(
        pathProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(provider {
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
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
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

        configurations[configurationName].dependencies.addLater(finalTypeProvider.zip(finalVersionProvider) { type, version ->
            when (type) {
                IntelliJPlatformType.AndroidStudio -> dependencies.createAndroidStudio(version)
                else -> dependencies.createIntelliJPlatform(type, version)
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
    ) = configurations[dependencyConfigurationName].dependencies.addAllLater(notationsProvider.map { notations ->
        notations.map { notation ->
            val (type, version) = notation.parseIdeNotation()

            when (type) {
                IntelliJPlatformType.AndroidStudio -> dependencies.createAndroidStudio(version)
                else -> dependencies.createIntelliJPlatform(type, version)
            }.apply(action).also { dependency ->
                val dependencyConfiguration = configurations.maybeCreate("${dependencyConfigurationName}_$notation").apply {
                    dependencies.add(dependency)
                }

                configurations.findByName("${configurationName}_$notation")
                    ?: configurations.create("${configurationName}_$notation").apply {
                        attributes {
                            attribute(Attributes.extracted, true)
                        }
                        extendsFrom(dependencyConfiguration)
                    }
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
    ) = configurations[configurationName].dependencies.addLater(localPathProvider.map { resolveArtifactPath(it) }.map { localPath ->
        dependencies.createIntelliJPlatformLocal(localPath).apply(action)
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
    ) = configurations[configurationName].dependencies.addAllLater(provider {
        val plugins = pluginsProvider.orNull
        requireNotNull(plugins) { "The `intellijPlatform.plugins` dependency helper was called with no `plugins` value provided." }

        plugins.map { (id, version, channel) ->
            dependencies.createIntelliJPlatformPlugin(id, version, channel).apply(action)
        }
    })

    /**
     * A base method for adding a dependency on a plugin for IntelliJ Platform.
     *
     * @param bundledPluginsProvider The provider of the list containing triples with plugin identifier, version, and channel.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addIntelliJPlatformBundledPluginDependencies(
        bundledPluginsProvider: Provider<List<String>>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(provider {
        val bundledPlugins = bundledPluginsProvider.orNull
        requireNotNull(bundledPlugins) { "The `intellijPlatform.bundledPlugins` dependency helper was called with no `bundledPlugins` value provided." }

        bundledPlugins.filter { id -> id.isNotBlank() }.map { id ->
            dependencies.createIntelliJPlatformBundledPlugin(id).apply(action)
        }
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
    ) = configurations[configurationName].dependencies.addLater(provider {
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
        dependencyVersion: DependencyVersion,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_JAVA_COMPILER,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        provider {
            dependencies.createDependency(
                subject = "Java Compiler",
                coordinates = Coordinates("com.jetbrains.intellij.java", "java-compiler-ant-tasks"),
                version = dependencyVersion,
            ).apply(action)
        }
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
    ) = configurations[configurationName].dependencies.addLater(provider {
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
     * A base method for adding a dependency on IntelliJ Plugin Verifier.
     *
     * @param version The provider of the IntelliJ Plugin Verifier version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addPluginVerifierDependency(
        version: DependencyVersion,
        configurationName: String = Configurations.INTELLIJ_PLUGIN_VERIFIER,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(provider {
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
     * @param typeProvider The TestFramework type provider.
     * @param versionProvider The version of the TestFramework.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addTestFrameworkDependency(
        type: TestFrameworkType,
        version: DependencyVersion,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addAllLater(provider {
        when (type) {
            TestFrameworkType.Bundled -> type.coordinates.map {
                dependencies.createBundledLibrary(it.artifactId)
            }

            else -> type.coordinates.map {
                dependencies.createPlatformDependency(
                    subject = "TestFramework",
                    coordinates = it,
                    version = version,
                )
            }
        }.onEach(action)
    })

    /**
     * Adds a dependency on the IntelliJ Platform dependency.
     *
     * This dependency belongs to IntelliJ Platform repositories.
     *
     * @param groupIdProvider The groupId of the IntelliJ Platform dependency.
     * @param artifactIdProvider The artifactId of the IntelliJ Platform dependency.
     * @param versionProvider The version of the IntelliJ Platform dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addPlatformDependency(
        coordinates: Coordinates,
        version: DependencyVersion,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(provider {
        dependencies.createPlatformDependency("IntelliJ Platform dependency", coordinates, version).apply(action)
    })

    /**
     * A base method for adding a dependency on Marketplace ZIP Signer.
     *
     * @param versionProvider The provider of the Marketplace ZIP Signer version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    internal fun addZipSignerDependency(
        version: DependencyVersion,
        configurationName: String = Configurations.MARKETPLACE_ZIP_SIGNER,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(provider {
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
        version: DependencyVersion,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(provider {
        dependencies.createRobotServerPlugin(version).apply(action)
    })

    internal fun createProductReleasesValueSource(configure: ProductReleasesValueSource.FilterParameters.() -> Unit) =
        providers.of(ProductReleasesValueSource::class.java) {
            parameters.jetbrainsIdes.set(resources.resolve(Locations.PRODUCTS_RELEASES_JETBRAINS_IDES))
            parameters.androidStudio.set(resources.resolve(Locations.PRODUCTS_RELEASES_ANDROID_STUDIO))

            parameters(configure)
        }


    //</editor-fold>

    //<editor-fold desc="DependencyHelper Extensions">

    /**
     * Creates Android Studio dependency.
     *
     * @param version Android Studio version
     */
    private fun DependencyHandler.createAndroidStudio(version: String): Dependency {
        val type = IntelliJPlatformType.AndroidStudio
        val downloadLink = providers.of(AndroidStudioDownloadLinkValueSource::class) {
            parameters {
                androidStudio = resources.resolve(Locations.PRODUCTS_RELEASES_ANDROID_STUDIO)
                androidStudioVersion = version
            }
        }.orNull

        requireNotNull(downloadLink) { "Couldn't resolve Android Studio download URL for version: $version" }
        requireNotNull(type.binary) { "Specified type '$type' has no artifact coordinates available." }

        val (classifier, extension) = downloadLink.substringAfter("$version-").split(".", limit = 2)

        return create(
            group = type.binary.groupId,
            name = type.binary.artifactId,
            classifier = classifier,
            ext = extension,
            version = version,
        )
    }

    /**
     * Creates a [Dependency] using a Jar file resolved in [platformPath] with [path].
     *
     * @param path relative path to the library, like: `lib/testFramework.jar`
     */
    private fun DependencyHandler.createBundledLibrary(path: String) = create(
        objects.fileCollection().from(platformPath.map {
            it.resolve(path)
        })
    )

    /**
     * Creates IntelliJ Platform dependency based on the provided version and type.
     *
     * @param version IntelliJ Platform version
     * @param type IntelliJ Platform type
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
     * Creates a dependency on a local IntelliJ Platform instance.
     *
     * @param localPath Path to the local IntelliJ Platform
     */
    private fun DependencyHandler.createIntelliJPlatformLocal(artifactPath: Path): Dependency {
        val localProductInfo = artifactPath.productInfo()

        localProductInfo.validateSupportedVersion()

        // TODO: remove [hash]
        val hash = artifactPath.hashCode().absoluteValue % 1000
        val type = localProductInfo.productCode.toIntelliJPlatformType()
        val coordinates = type.maven ?: type.binary
        requireNotNull(coordinates) { "Specified type '$type' has no dependency available." }

        return create(
            group = Configurations.Dependencies.LOCAL_IDE_GROUP,
            name = coordinates.groupId,
            version = "${localProductInfo.version}+$hash",
        ).apply {
            createIvyDependencyFile(
                localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
                publications = listOf(artifactPath.toPublication()),
            )
        }
    }

    /**
     * Creates a dependency for an IntelliJ Platform plugin.
     *
     * @param pluginId the ID of the plugin
     * @param version the version of the plugin
     * @param channel the channel of the plugin. Can be null or empty for the default channel.
     */
    private fun DependencyHandler.createIntelliJPlatformPlugin(pluginId: String, version: String, channel: String?): Dependency {
        val group = when (channel) {
            "default", "", null -> JETBRAINS_MARKETPLACE_MAVEN_GROUP
            else -> "$channel.$JETBRAINS_MARKETPLACE_MAVEN_GROUP"
        }

        return create(
            group = group,
            name = pluginId.trim(),
            version = version,
        )
    }

    /**
     * Creates a dependency for an IntelliJ platform bundled plugin.
     *
     * @param bundledPluginId The ID of the bundled plugin
     */
    private fun DependencyHandler.createIntelliJPlatformBundledPlugin(bundledPluginId: String): Dependency {
        val id = bundledPluginId.trim()
        val plugin = bundledPlugins.get().plugins.find { it.id == id }
        requireNotNull(plugin) { "Could not find bundled plugin with ID: '$id'" }

        val version = productInfo.get().version
        val artifactPath = platformPath.get()
        val hash = artifactPath.hashCode().absoluteValue % 1000
        val hashedVersion = "$version+$hash"

        return create(
            group = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
            name = plugin.id,
            version = hashedVersion,
        ).apply {
            createBundledPluginIvyDependencyFile(plugin, hashedVersion)
        }
    }

    /**
     * Creates a dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin
     */
    private fun DependencyHandler.createIntelliJPlatformLocalPlugin(localPath: Any): Dependency {
        val artifactPath = when (localPath) {
            is String -> localPath
            is File -> localPath.absolutePath
            is Directory -> localPath.asPath.pathString
            else -> throw IllegalArgumentException("Invalid argument type: '${localPath.javaClass}'. Supported types: String, File, or Directory.")
        }.let { Path(it) }.takeIf { it.exists() }.let { requireNotNull(it) { "Specified localPath '$localPath' doesn't exist." } }

        val plugin by lazy {
            val pluginPath = when {
                artifactPath.isDirectory() -> generateSequence(artifactPath) {
                    it.takeIf { it.resolve("lib").exists() } ?: it.listDirectoryEntries().singleOrNull()
                }.firstOrNull { it.resolve("lib").exists() } ?: throw GradleException("Could not resolve plugin directory: $artifactPath")

                else -> artifactPath
            }

            val pluginCreationResult = IdePluginManager.createManager().createPlugin(pluginPath, false)

            require(pluginCreationResult is PluginCreationSuccess)
            pluginCreationResult.plugin
        }

        return create(
            group = Configurations.Dependencies.LOCAL_PLUGIN_GROUP,
            name = plugin.pluginId ?: artifactPath.name,
            version = plugin.pluginVersion ?: "0.0.0",
        ).apply {
            createIvyDependencyFile(
                localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
                publications = listOf(artifactPath.toPublication()),
            )
        }
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
    private fun DependencyHandler.createPluginVerifier(version: DependencyVersion) = createDependency(
        subject = "IntelliJ Plugin Verifier",
        coordinates = Coordinates("org.jetbrains.intellij.plugins", "verifier-cli"),
        version = version,
        classifier = "all",
        extension = "jar",
    )

    /**
     * Creates a dependency.
     *
     * @param subject Dependency name
     * @param coordinates Dependency coordinates
     * @param version Dependency version
     */
    private fun DependencyHandler.createDependency(
        subject: String,
        coordinates: Coordinates,
        version: DependencyVersion,
        classifier: String? = null,
        extension: String? = null,
    ) = create(
        group = coordinates.groupId,
        name = coordinates.artifactId,
        version = version.resolve(subject, coordinates).get().toString(),
        classifier = classifier,
        ext = extension,
    )

    /**
     * Creates an IntelliJ Platform dependency and excludes transitive dependencies provided by the current IntelliJ Platform.
     *
     * @param subject Dependency name
     * @param coordinates Dependency coordinates
     * @param version Dependency version
     */
    private fun DependencyHandler.createPlatformDependency(
        subject: String,
        coordinates: Coordinates,
        version: DependencyVersion,
    ) = createDependency(subject, coordinates, version).apply {
        val moduleDescriptors = providers.of(ModuleDescriptorsValueSource::class) {
            parameters {
                intellijPlatformPath = layout.dir(platformPath.map { it.toFile() })
            }
        }

        moduleDescriptors.get().forEach {
            exclude(it.groupId, it.artifactId)
        }
    }

    private fun DependencyVersion.resolve(subject: String, coordinates: Coordinates) = when (this) {
        is DependencyVersion.IntelliJPlatform -> productInfo
            .map { it.buildNumber.toVersion() }

        is DependencyVersion.Closest -> productInfo
            .map { it.buildNumber.toVersion() }
            .map { buildNumber ->
                ClosestVersionResolver(
                    subject = subject,
                    coordinates = coordinates,
                    version = buildNumber,
                    urls = repositoryUrls,
                    resources = resources,
                ).resolve()
            }

        is DependencyVersion.Latest -> provider {
            LatestVersionResolver(
                subject = subject,
                coordinates = coordinates,
                urls = repositoryUrls,
                resources = resources,
            ).resolve()
        }

        is DependencyVersion.Exact -> provider { version.toVersion() }
    }

    /**
     * Adds a dependency on a Marketplace ZIP Signer required for signing plugin with [SignPluginTask].
     *
     * @param version Marketplace ZIP Signer version
     */
    private fun DependencyHandler.createMarketplaceZipSigner(version: DependencyVersion) = createDependency(
        subject = "Marketplace ZIP Signer",
        coordinates = Coordinates("org.jetbrains", "marketplace-zip-signer"),
        version = version,
        classifier = "cli",
        extension = "jar",
    )

    /**
     * Adds a dependency on a Robot Server Plugin required for signing plugin with [TestIdeUiTask].
     *
     * @param version Robot Server Plugin version
     */
    private fun DependencyHandler.createRobotServerPlugin(version: DependencyVersion) = createDependency(
        subject = "Robot Server Plugin",
        coordinates = Coordinates("com.intellij.remoterobot", "robot-server-plugin"),
        version = version,
        extension = "zip",
    )

    // TODO: cleanup and migrate to Ivy webserver
    private fun createBundledPluginIvyDependencyFile(bundledPlugin: BundledPlugin, version: String, resolved: List<String> = emptyList()) {
        val localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory)
        val group = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP
        val name = bundledPlugin.id
        val existingIvyFile = localPlatformArtifactsPath.resolve("$group-$name-$version.xml")
        if (existingIvyFile.exists()) {
            return
        }

        if (name in resolved) {
            return
        }

        val artifactPath = Path(bundledPlugin.path)
        val plugin by lazy {
            val pluginCreationResult = IdePluginManager.createManager().createPlugin(artifactPath, false)
            require(pluginCreationResult is PluginCreationSuccess)
            pluginCreationResult.plugin
        }

        val dependencyIds = plugin.dependencies.map { it.id } - plugin.pluginId
        val bundledModules = productInfo.get().layout.filter { layout -> layout.name in dependencyIds }.filter { layout -> layout.classPath.isNotEmpty() }

        /**
         * This is a fallback method for IntelliJ Platform with no [ProductInfo.layout] data present.
         * To avoid duplications, exclude IDs of possibly resolved `bundledModules` items.
         *
         * Eventually, this data should be provided by the IntelliJ Plugin Verifier.
         */
        val fallbackBundledPlugins = run {
            val bundledModuleNames = bundledModules.map { it.name }

            bundledPlugins.get().plugins.filter {
                it.id in dependencyIds && it.id !in bundledModuleNames
            }
        }

        val ivyDependencies = bundledModules.mapNotNull { layout ->
            when (layout.kind) {
                ProductInfo.LayoutItemKind.plugin -> {
                    IvyModule.Dependency(
                        organization = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
                        name = layout.name,
                        version = version,
                    ).also { dependency ->
                        val dependencyPlugin = bundledPlugins.get().plugins.find { it.id == dependency.name } ?: return@also
                        createBundledPluginIvyDependencyFile(dependencyPlugin, version, resolved + name)
                    }
                }

                ProductInfo.LayoutItemKind.pluginAlias -> {
                    // TODO: not important?
                    null
                }

                ProductInfo.LayoutItemKind.moduleV2, ProductInfo.LayoutItemKind.productModuleV2 -> {
                    // TODO: drop if classPath empty?
                    IvyModule.Dependency(
                        organization = Configurations.Dependencies.BUNDLED_MODULE_GROUP,
                        name = layout.name,
                        version = version,
                    ).also { dependency ->
                        createIvyDependencyFile(
                            group = dependency.organization,
                            name = dependency.name,
                            version = dependency.version,
                            localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
                            publications = layout.classPath.map { classPath ->
                                platformPath.get().resolve(classPath).toPublication()
                            },
                        )
                    }
                }
            }
        } + fallbackBundledPlugins.map { dependencyPlugin ->
            IvyModule.Dependency(
                organization = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
                name = dependencyPlugin.id,
                version = version,
            ).also {
                createBundledPluginIvyDependencyFile(dependencyPlugin, version, resolved + name)
            }
        }

        createIvyDependencyFile(
            group = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
            name = bundledPlugin.id,
            version = version,
            localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
            publications = listOf(artifactPath.toPublication()),
            dependencies = ivyDependencies,
        )
    }

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

    //</editor-fold>
}
