// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.Directory
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.JETBRAINS_MARKETPLACE_MAVEN_GROUP
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.VERSION_LATEST
import org.jetbrains.intellij.platform.gradle.model.assertSupportedVersion
import org.jetbrains.intellij.platform.gradle.model.bundledPlugins
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.model.toPublication
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.*
import java.io.File
import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.math.absoluteValue

/**
 * Extension class for managing IntelliJ Platform dependencies in a Gradle build script applied to the [DependencyHandler].
 *
 * This class provides methods for adding dependencies to different IntelliJ Platform products and managing local dependencies.
 * It also includes methods for adding JetBrains Runtime, IntelliJ Platform plugins, IntelliJ Platform bundled plugins, IntelliJ Plugin Verifier,
 * and Marketplace ZIP Signer.
 *
 * @param configurations The Gradle [ConfigurationContainer] to manage configurations.
 * @param repositories The Gradle [RepositoryHandler] to manage repositories.
 * @param dependencies The Gradle [DependencyHandler] to manage dependencies.
 * @param providers The Gradle [ProviderFactory] to create providers.
 * @param gradle The [Gradle] instance.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@IntelliJPlatform
abstract class IntelliJPlatformDependenciesExtension @Inject constructor(
    private val configurations: ConfigurationContainer,
    private val repositories: RepositoryHandler,
    private val dependencies: DependencyHandler,
    private val providers: ProviderFactory,
    private val gradle: Gradle,
) {

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The version of the IntelliJ Platform dependency.
     */
    fun create(type: String, version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { type.toIntelliJPlatformType() },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param version The version of the IntelliJ Platform dependency.
     */
    fun create(type: Provider<*>, version: String) = addIntelliJPlatformDependency(
        typeProvider = type,
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The version of the IntelliJ Platform dependency.
     */
    fun create(type: IntelliJPlatformType, version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { type },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The provider for the version of the IntelliJ Platform dependency.
     */
    fun create(type: String, version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { type.toIntelliJPlatformType() },
        versionProvider = version,
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The provider for the version of the IntelliJ Platform dependency.
     */
    fun create(type: IntelliJPlatformType, version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { type },
        versionProvider = version,
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param version The provider for the version of the IntelliJ Platform dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     */
    fun create(type: Provider<*>, version: Provider<String>, configurationName: String) = addIntelliJPlatformDependency(
        typeProvider = type,
        versionProvider = version,
        configurationName = configurationName,
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param version The provider for the version of the IntelliJ Platform dependency.
     */
    fun create(type: Provider<*>, version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = type,
        versionProvider = version,
    )

    /**
     * Adds a dependency on Android Studio.
     *
     * @param version The version of Android Studio.
     */
    fun androidStudio(version: String) = create(
        type = IntelliJPlatformType.AndroidStudio,
        version = version,
    )

    /**
     * Adds a dependency on Android Studio.
     *
     * @param version The provider for the version of Android Studio.
     */
    fun androidStudio(version: Provider<String>) = create(
        type = IntelliJPlatformType.AndroidStudio,
        version = version
    )

    /**
     * Adds a dependency on CLion.
     *
     * @param version The version of CLion.
     */
    fun clion(version: String) = create(
        type = IntelliJPlatformType.CLion,
        version = version,
    )

    /**
     * Adds a dependency on CLion.
     *
     * @param version The provider for the version of CLion.
     */
    fun clion(version: Provider<String>) = create(
        type = IntelliJPlatformType.CLion,
        version = version,
    )

    /**
     * Adds a dependency on Fleet Backend.
     *
     * @param version The version of Fleet Backend.
     */
    fun fleetBackend(version: String) = create(
        type = IntelliJPlatformType.Fleet,
        version = version,
    )

    /**
     * Adds a dependency on Fleet Backend.
     *
     * @param version The provider for the version of Fleet Backend.
     */
    fun fleetBackend(version: Provider<String>) = create(
        type = IntelliJPlatformType.Fleet,
        version = version,
    )

    /**
     * Adds a dependency on Gateway.
     *
     * @param version The version of Gateway.
     */
    fun gateway(version: String) = create(
        type = IntelliJPlatformType.Gateway,
        version = version,
    )

    /**
     * Adds a dependency on Gateway.
     *
     * @param version The provider for the version of Gateway.
     */
    fun gateway(version: Provider<String>) = create(
        type = IntelliJPlatformType.Gateway,
        version = version,
    )

    /**
     * Adds a dependency on GoLand.
     *
     * @param version The version of GoLand.
     */
    fun goland(version: String) = create(
        type = IntelliJPlatformType.GoLand,
        version = version,
    )

    /**
     * Adds a dependency on GoLand.
     *
     * @param version The provider for the version of GoLand.
     */
    fun goland(version: Provider<String>) = create(
        type = IntelliJPlatformType.GoLand,
        version = version,
    )

    /**
     * Adds a dependency on IntelliJ IDEA Community.
     *
     * @param version The version of IntelliJ IDEA Community.
     */
    fun intellijIdeaCommunity(version: String) = create(
        type = IntelliJPlatformType.IntellijIdeaCommunity,
        version = version,
    )

    /**
     * Adds a dependency on IntelliJ IDEA Community.
     *
     * @param version The provider for the version of IntelliJ IDEA Community.
     */
    fun intellijIdeaCommunity(version: Provider<String>) = create(
        type = IntelliJPlatformType.IntellijIdeaCommunity,
        version = version,
    )

    /**
     * Adds a dependency on IntelliJ IDEA Ultimate.
     *
     * @param version The version of IntelliJ IDEA Ultimate.
     */
    fun intellijIdeaUltimate(version: String) = create(
        type = IntelliJPlatformType.IntellijIdeaUltimate,
        version = version,
    )

    /**
     * Adds a dependency on IntelliJ IDEA Ultimate.
     *
     * @param version The provider for the version of IntelliJ IDEA Ultimate.
     */
    fun intellijIdeaUltimate(version: Provider<String>) = create(
        type = IntelliJPlatformType.IntellijIdeaUltimate,
        version = version,
    )

    /**
     * Adds a dependency on PhpStorm.
     *
     * @param version The version of PhpStorm.
     */
    fun phpstorm(version: String) = create(
        type = IntelliJPlatformType.PhpStorm,
        version = version,
    )

    /**
     * Adds a dependency on PhpStorm.
     *
     * @param version The provider for the version of PhpStorm.
     */
    fun phpstorm(version: Provider<String>) = create(
        type = IntelliJPlatformType.PhpStorm,
        version = version,
    )

    /**
     * Adds a dependency on PyCharm Community.
     *
     * @param version The version of PyCharm Community.
     */
    fun pycharmCommunity(version: String) = create(
        type = IntelliJPlatformType.PyCharmCommunity,
        version = version,
    )

    /**
     * Adds a dependency on PyCharm Community.
     *
     * @param version The provider for the version of PyCharm Community.
     */
    fun pycharmCommunity(version: Provider<String>) = create(
        type = IntelliJPlatformType.PyCharmCommunity,
        version = version,
    )

    /**
     * Adds a dependency on PyCharm Professional.
     *
     * @param version The version of PyCharm Professional.
     */
    fun pycharmProfessional(version: String) = create(
        type = IntelliJPlatformType.PyCharmProfessional,
        version = version,
    )

    /**
     * Adds a dependency on PyCharm Professional.
     *
     * @param version The provider for the version of PyCharm Professional.
     */
    fun pycharmProfessional(version: Provider<String>) = create(
        type = IntelliJPlatformType.PyCharmProfessional,
        version = version,
    )

    /**
     * Adds a dependency on Rider.
     *
     * @param version The version of Rider.
     */
    fun rider(version: String) = create(
        type = IntelliJPlatformType.Rider,
        version = version,
    )

    /**
     * Adds a dependency on Rider.
     *
     * @param version The provider for the version of Rider.
     */
    fun rider(version: Provider<String>) = create(
        type = IntelliJPlatformType.Rider,
        version = version,
    )

    /**
     * Adds a dependency on Rust Rover.
     *
     * @param version The version of Rust Rover.
     */
    fun rustRover(version: String) = create(
        type = IntelliJPlatformType.RustRover,
        version = version,
    )

    /**
     * Adds a dependency on Rust Rover.
     *
     * @param version The provider for the version of Rust Rover.
     */
    fun rustRover(version: Provider<String>) = create(
        type = IntelliJPlatformType.RustRover,
        version = version,
    )

    /**
     * Adds a dependency on Writerside.
     *
     * @param version The version of Writerside.
     */
    fun writerside(version: String) = create(
        type = IntelliJPlatformType.Writerside,
        version = version,
    )

    /**
     * Adds a dependency on Writerside.
     *
     * @param version The provider for the version of Writerside.
     */
    fun writerside(version: Provider<String>) = create(
        type = IntelliJPlatformType.Writerside,
        version = version,
    )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The local path of the IntelliJ Platform dependency
     */
    fun local(localPath: String) = addIntelliJPlatformLocalDependency(
        localPathProvider = providers.provider { localPath },
    )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The local path of the IntelliJ Platform dependency
     */
    fun local(localPath: File) = addIntelliJPlatformLocalDependency(
        localPathProvider = providers.provider { localPath },
    )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The local path of the IntelliJ Platform dependency
     */
    fun local(localPath: Directory) = addIntelliJPlatformLocalDependency(
        localPathProvider = providers.provider { localPath },
    )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     */
    fun local(localPath: Provider<*>) = addIntelliJPlatformLocalDependency(
        localPathProvider = localPath,
    )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     * @param configurationName The name of the configuration to add the dependency to.
     */
    fun local(localPath: Provider<*>, configurationName: String) = addIntelliJPlatformLocalDependency(
        localPathProvider = localPath,
        configurationName = configurationName,
    )

    /**
     * Adds a dependency on JetBrains Runtime.
     *
     * @param explicitVersion The explicit version of the JetBrains Runtime.
     */
    fun jetbrainsRuntime(explicitVersion: String) = addJetBrainsRuntimeDependency(
        explicitVersionProvider = providers.provider { explicitVersion },
    )

    /**
     * Adds a dependency on JetBrains Runtime.
     *
     * @param explicitVersion The provider for the explicit version of the JetBrains Runtime.
     */
    fun jetbrainsRuntime(explicitVersion: Provider<String>) = addJetBrainsRuntimeDependency(
        explicitVersionProvider = explicitVersion,
    )

    /**
     * Adds a local dependency on JetBrains Runtime.
     *
     * @param version The JetBrains Runtime version.
     * @param variant The JetBrains Runtime variant.
     * @param architecture The JetBrains Runtime architecture.
     */
    fun jetbrainsRuntime(version: String, variant: String, architecture: String) = addJetBrainsRuntimeDependency(
        explicitVersionProvider = providers.provider { from(version, variant, architecture) },
    )

    /**
     * Adds a local dependency on JetBrains Runtime.
     *
     * @param version The provider of the JetBrains Runtime version.
     * @param variant The provider of the JetBrains Runtime variant.
     * @param architecture The provider of the JetBrains Runtime architecture.
     */
    fun jetbrainsRuntime(version: Provider<String>, variant: Provider<String>, architecture: Provider<String>) = addJetBrainsRuntimeDependency(
        explicitVersionProvider = providers.provider { from(version.get(), variant.orNull, architecture.orNull) },
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The plugin identifier.
     * @param version The plugin version.
     * @param channel The plugin distribution channel.
     */
    fun plugin(id: String, version: String, channel: String = "") = addIntelliJPlatformPluginDependencies(
        plugins = providers.provider { listOf(Triple(id, version, channel)) }
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The provider of the plugin identifier.
     * @param version The provider of the plugin version.
     * @param channel The provider of the plugin distribution channel.
     */
    fun plugin(id: Provider<String>, version: Provider<String>, channel: Provider<String>) = addIntelliJPlatformPluginDependencies(
        plugins = providers.provider { listOf(Triple(id.get(), version.get(), channel.get())) }
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: Provider<String>) = addIntelliJPlatformPluginDependencies(
        plugins = notation.map { listOf(it.parsePluginNotation()) }
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: Provider<List<String>>) = addIntelliJPlatformPluginDependencies(
        plugins = notations.map { it.map { notation -> notation.parsePluginNotation() } }
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: String) = addIntelliJPlatformPluginDependencies(
        plugins = providers.provider { listOf(notation.parsePluginNotation()) }
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(vararg notations: String) = addIntelliJPlatformPluginDependencies(
        plugins = providers.provider { notations.map { it.parsePluginNotation() } }
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: List<String>) = addIntelliJPlatformPluginDependencies(
        plugins = providers.provider { notations.map { it.parsePluginNotation() } }
    )

    /**
     * Adds a dependency on a bundled IntelliJ Platform plugin.
     *
     * @param id The provider of the bundled plugin identifier.
     */
    fun bundledPlugin(id: Provider<String>) = addIntelliJPlatformBundledPluginDependencies(
        bundledPlugins = id.map { listOf(it) }
    )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(ids: Provider<List<String>>) = addIntelliJPlatformBundledPluginDependencies(
        bundledPlugins = ids
    )

    /**
     * Adds a dependency on a bundled IntelliJ Platform plugin.
     *
     * @param id The bundled plugin identifier.
     */
    fun bundledPlugin(id: String) = addIntelliJPlatformBundledPluginDependencies(
        bundledPlugins = providers.provider { listOf(id) }
    )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(vararg ids: String) = addIntelliJPlatformBundledPluginDependencies(
        bundledPlugins = providers.provider { ids.asList() }
    )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(ids: List<String>) = addIntelliJPlatformBundledPluginDependencies(
        bundledPlugins = providers.provider { ids }
    )

    /**
     * Adds a dependency on IntelliJ Plugin Verifier.
     *
     * @param version The provider of the IntelliJ Plugin Verifier version.
     * @param configurationName The name of the configuration to add the dependency to. Defaults to [Configurations.INTELLIJ_PLUGIN_VERIFIER].
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    fun pluginVerifier(
        version: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLUGIN_VERIFIER,
        action: DependencyAction = {},
    ) = addPluginVerifier(version, configurationName, action)

    /**
     * Adds a dependency on IntelliJ Plugin Verifier.
     *
     * @param version The IntelliJ Plugin Verifier version.
     * @param configurationName The name of the configuration to add the dependency to. Defaults to [Configurations.INTELLIJ_PLUGIN_VERIFIER].
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    fun pluginVerifier(
        version: String = VERSION_LATEST,
        configurationName: String = Configurations.INTELLIJ_PLUGIN_VERIFIER,
        action: DependencyAction = {},
    ) = pluginVerifier(providers.provider { version }, configurationName, action)

    /**
     * Adds a dependency on Marketplace ZIP Signer.
     *
     * @param version The provider of the Marketplace ZIP Signer version.
     * @param configurationName The name of the configuration to add the dependency to. Defaults to [Configurations.MARKETPLACE_ZIP_SIGNER].
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    fun zipSigner(
        version: Provider<String>,
        configurationName: String = Configurations.MARKETPLACE_ZIP_SIGNER,
        action: DependencyAction = {},
    ) = addZipSigner(version, configurationName, action)

    /**
     * Adds a dependency on Marketplace ZIP Signer.
     *
     * @param version The Marketplace ZIP Signer version.
     * @param configurationName The name of the configuration to add the dependency to. Defaults to [Configurations.MARKETPLACE_ZIP_SIGNER].
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    fun zipSigner(
        version: String = VERSION_LATEST,
        configurationName: String = Configurations.MARKETPLACE_ZIP_SIGNER,
        action: DependencyAction = {},
    ) = zipSigner(providers.provider { version }, configurationName, action)

    /**
     * A base method for adding a dependency on IntelliJ Platform.
     *
     * @param typeProvider The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param versionProvider The provider for the version of the IntelliJ Platform dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action An optional action to be performed on the created dependency.
     */
    private fun addIntelliJPlatformDependency(
        typeProvider: Provider<*>,
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = dependencies.addProvider(
        configurationName,
        typeProvider.map {
            when (it) {
                is IntelliJPlatformType -> it
                is String -> it.toIntelliJPlatformType()
                else -> throw IllegalArgumentException("Invalid argument type: ${it.javaClass}. Supported types: String or ${IntelliJPlatformType::class.java}")
            }
        }.zip(versionProvider) { type, version ->
            dependencies.create(
                group = type.dependency.group,
                name = type.dependency.name,
                version = version,
            )
        },
        action,
    )

    /**
     * A base method for adding a dependency on a local IntelliJ Platform instance.
     *
     * @param localPathProvider The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action An optional action to be performed on the created dependency.
     */
    private fun addIntelliJPlatformLocalDependency(
        localPathProvider: Provider<*>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_LOCAL_INSTANCE,
        action: DependencyAction = {},
    ) = dependencies.addProvider(
        configurationName,
        localPathProvider.map { localPath ->
            val artifactPath = resolveArtifactPath(localPath)
            val productInfo = artifactPath.productInfo()

            productInfo.assertSupportedVersion()

            val type = productInfo.productCode.toIntelliJPlatformType()
            val hash = artifactPath.pathString.hashCode().absoluteValue % 1000

            dependencies.create(
                group = Configurations.Dependencies.LOCAL_IDE_GROUP,
                name = type.dependency.name,
                version = "${productInfo.version}+$hash",
            ).apply {
                createIvyDependency(gradle, listOf(artifactPath.toPublication()))
            }
        },
        action,
    )

    /**
     * A base method for adding a dependency on JetBrains Runtime.
     *
     * @param explicitVersionProvider The provider for the explicit version of the JetBrains Runtime.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    private fun addJetBrainsRuntimeDependency(
        explicitVersionProvider: Provider<String>,
        configurationName: String = Configurations.JETBRAINS_RUNTIME_DEPENDENCY,
        action: DependencyAction = {},
    ) = dependencies.addProvider(
        configurationName,
        explicitVersionProvider.map {
            dependencies.create(
                group = "com.jetbrains",
                name = "jbr",
                version = it,
                ext = "tar.gz",
            )
        },
        action,
    )

    /**
     * A base method for adding a dependency on a plugin for IntelliJ Platform.
     *
     * @param plugins The provider of the list containing triples with plugin identifier, version, and channel.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    private fun addIntelliJPlatformPluginDependencies(
        plugins: Provider<List<Triple<String, String, String>>>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGINS,
        action: DependencyAction = {},
    ) = configurations.getByName(configurationName).dependencies.addAllLater(
        plugins.map {
            it.map { (id, version, channel) -> createIntelliJPlatformPluginDependency(id, version, channel).apply(action) }
        }
    )

    /**
     * Creates a dependency for an IntelliJ platform plugin.
     *
     * @param id the ID of the plugin
     * @param version the version of the plugin
     * @param channel the channel of the plugin. Can be null or empty for the default channel.
     */
    private fun createIntelliJPlatformPluginDependency(id: String, version: String, channel: String?): Dependency {
        val group = when (channel) {
            "default", "", null -> JETBRAINS_MARKETPLACE_MAVEN_GROUP
            else -> "$channel.$JETBRAINS_MARKETPLACE_MAVEN_GROUP"
        }

        return dependencies.create(
            group = group,
            name = id.trim(),
            version = version,
        )
    }

    /**
     * A base method for adding a dependency on a plugin for IntelliJ Platform.
     *
     * @param bundledPlugins The provider of the list containing triples with plugin identifier, version, and channel.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    private fun addIntelliJPlatformBundledPluginDependencies(
        bundledPlugins: Provider<List<String>>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS,
        action: DependencyAction = {},
    ) = configurations.getByName(configurationName).dependencies.addAllLater(
        bundledPlugins.map {
            it.map { id -> createIntelliJPlatformBundledPluginDependency(id).apply(action) }
        }
    )

    /**
     * Creates a dependency for an IntelliJ platform bundled plugin.
     *
     * @param bundledPluginId The ID of the bundled plugin.
     */
    private fun createIntelliJPlatformBundledPluginDependency(bundledPluginId: String): Dependency {
        val id = bundledPluginId.trim()
        val productInfo = configurations.getByName(Configurations.INTELLIJ_PLATFORM).productInfo()
        val bundledPluginsList = configurations.getByName(Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS_LIST).single().toPath().bundledPlugins()
        val bundledPlugin = bundledPluginsList.plugins.find { it.id == id }.throwIfNull { throw Exception("Could not find bundled plugin: $id") }
        val artifactPath = Path(bundledPlugin.path)
        val jars = artifactPath.resolve("lib").listDirectoryEntries("*.jar")
        val hash = artifactPath.pathString.hashCode().absoluteValue % 1000

        return dependencies.create(
            group = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
            name = id,
            version = "${productInfo.version}+$hash",
        ).apply {
            createIvyDependency(gradle, jars.map { it.toPublication() })
        }
    }

    /**
     * A base method for adding  a dependency on IntelliJ Plugin Verifier.
     *
     * @param versionProvider The provider of the IntelliJ Plugin Verifier version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    private fun addPluginVerifier(
        versionProvider: Provider<String>,
        configurationName: String,
        action: DependencyAction = {},
    ) = dependencies.addProvider(
        configurationName,
        versionProvider.map { version ->
            dependencies.create(
                group = "org.jetbrains.intellij.plugins",
                name = "verifier-cli",
                version = when (version) {
                    VERSION_LATEST -> LatestVersionResolver.pluginVerifier()
                    else -> version
                },
                classifier = "all",
                ext = "jar",
            )
        },
        action,
    )

    /**
     * A base method for adding a dependency on Marketplace ZIP Signer.
     *
     * @param versionProvider The provider of the Marketplace ZIP Signer version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    private fun addZipSigner(
        versionProvider: Provider<String>,
        configurationName: String,
        action: DependencyAction = {},
    ) = dependencies.addProvider(
        configurationName,
        versionProvider.map { version ->
            dependencies.create(
                group = "org.jetbrains",
                name = "marketplace-zip-signer",
                version = when (version) {
                    VERSION_LATEST -> LatestVersionResolver.zipSigner()
                    else -> version
                },
                classifier = "cli",
                ext = "jar",
            )
        },
        action,
    )
}

/**
 * Parses a string in plugin notation and returns a triple containing the plugin ID, version, and channel.
 */
private fun String.parsePluginNotation() = Triple(
    substringBefore(':'), // pluginId
    substringAfter(':').substringBefore('@'), // version
    substringAfter('@'), // channel
)

// TODO: cleanup JBR helper functions:
private fun from(jbrVersion: String, jbrVariant: String?, jbrArch: String?, operatingSystem: OperatingSystem = OperatingSystem.current()): String {
    val version = "8".takeIf { jbrVersion.startsWith('u') }.orEmpty() + jbrVersion
    var prefix = getPrefix(version, jbrVariant)
    val lastIndexOfB = version.lastIndexOf('b')
    val lastIndexOfDash = version.lastIndexOf('-') + 1
    val majorVersion = when (lastIndexOfB > -1) {
        true -> version.substring(lastIndexOfDash, lastIndexOfB)
        false -> version.substring(lastIndexOfDash)
    }
    val buildNumberString = when (lastIndexOfB > -1) {
        (lastIndexOfDash == lastIndexOfB) -> version.substring(0, lastIndexOfDash - 1)
        true -> version.substring(lastIndexOfB + 1)
        else -> ""
    }
    val buildNumber = buildNumberString.toVersion()
    val isJava8 = majorVersion.startsWith("8")
    val isJava17 = majorVersion.startsWith("17")
    val isJava21 = majorVersion.startsWith("21")

    val oldFormat = prefix == "jbrex" || isJava8 && buildNumber < "1483.24".toVersion()
    if (oldFormat) {
        return "jbrex${majorVersion}b${buildNumberString}_${platform(operatingSystem)}_${arch(false)}"
    }

    val arch = jbrArch ?: arch(isJava8)
    if (prefix.isEmpty()) {
        prefix = when {
            isJava17 || isJava21 -> "jbr_jcef-"
            isJava8 -> "jbrx-"
            operatingSystem.isMacOsX && arch == "aarch64" -> "jbr_jcef-"
            buildNumber < "1319.6".toVersion() -> "jbr-"
            else -> "jbr_jcef-"
        }
    }

    return "$prefix$majorVersion-${platform(operatingSystem)}-$arch-b$buildNumberString"
}

private fun getPrefix(version: String, variant: String?) = when {
    !variant.isNullOrEmpty() -> when (variant) {
        "sdk" -> "jbrsdk-"
        else -> "jbr_$variant-"
    }

    version.startsWith("jbrsdk-") -> "jbrsdk-"
    version.startsWith("jbr_jcef-") -> "jbr_jcef-"
    version.startsWith("jbr_dcevm-") -> "jbr_dcevm-"
    version.startsWith("jbr_fd-") -> "jbr_fd-"
    version.startsWith("jbr_nomod-") -> "jbr_nomod-"
    version.startsWith("jbr-") -> "jbr-"
    version.startsWith("jbrx-") -> "jbrx-"
    version.startsWith("jbrex8") -> "jbrex"
    else -> ""
}

private fun platform(operatingSystem: OperatingSystem) = when {
    operatingSystem.isWindows -> "windows"
    operatingSystem.isMacOsX -> "osx"
    else -> "linux"
}

private fun arch(newFormat: Boolean): String {
    val arch = System.getProperty("os.arch")
    if ("aarch64" == arch || "arm64" == arch) {
        return "aarch64"
    }
    if ("x86_64" == arch || "amd64" == arch) {
        return "x64"
    }
    val name = System.getProperty("os.name")
    if (name.contains("Windows") && System.getenv("ProgramFiles(x86)") != null) {
        return "x64"
    }
    return when (newFormat) {
        true -> "i586"
        false -> "x86"
    }
}

/**
 * Type alias for a lambda function that takes a [Dependency] and performs some actions on it.
 */
internal typealias DependencyAction = (Dependency.() -> Unit)
