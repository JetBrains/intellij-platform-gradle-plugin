// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.GradleException
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.JETBRAINS_MARKETPLACE_MAVEN_GROUP
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.Constants.VERSION_CURRENT
import org.jetbrains.intellij.platform.gradle.Constants.VERSION_LATEST
import org.jetbrains.intellij.platform.gradle.IntelliJPlatform
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.providers.AndroidStudioDownloadLinkValueSource
import org.jetbrains.intellij.platform.gradle.providers.ModuleDescriptorsValueSource
import org.jetbrains.intellij.platform.gradle.resolvers.closestVersion.JavaCompilerClosestVersionResolver
import org.jetbrains.intellij.platform.gradle.resolvers.closestVersion.TestFrameworkClosestVersionResolver
import org.jetbrains.intellij.platform.gradle.resolvers.latestVersion.IntelliJPluginVerifierLatestVersionResolver
import org.jetbrains.intellij.platform.gradle.resolvers.latestVersion.MarketplaceZipSignerLatestVersionResolver
import org.jetbrains.intellij.platform.gradle.tasks.InstrumentCodeTask
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.*
import java.io.File
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.math.absoluteValue

// TODO synchronize with
// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html

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
 * @param layout The Gradle [ProjectLayout] to manage layout providers.
 * @param rootProjectDirectory The root project directory location.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@IntelliJPlatform
abstract class IntelliJPlatformDependenciesExtension @Inject constructor(
    private val configurations: ConfigurationContainer,
    private val repositories: RepositoryHandler,
    private val dependencies: DependencyHandler,
    private val providers: ProviderFactory,
    private val resources: ResourceHandler,
    private val objects: ObjectFactory,
    private val layout: ProjectLayout,
    private val rootProjectDirectory: Path,
) {

    private val intelliJPlatformConfiguration = configurations[Configurations.INTELLIJ_PLATFORM].asLenient

    private val productInfo
        get() = intelliJPlatformConfiguration.productInfo()

    private val platformPath
        get() = intelliJPlatformConfiguration.platformPath()

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
    fun androidStudio(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.AndroidStudio },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on Android Studio.
     *
     * @param version The provider for the version of Android Studio.
     */
    fun androidStudio(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.AndroidStudio },
        versionProvider = version,
    )

    /**
     * Adds a dependency on Aqua.
     *
     * @param version The version of Aqua.
     */
    fun aqua(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.Aqua },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on Aqua.
     *
     * @param version The provider for the version of Aqua.
     */
    fun aqua(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.Aqua },
        versionProvider = version,
    )

    /**
     * Adds a dependency on DataGrip.
     *
     * @param version The version of DataGrip.
     */
    fun datagrip(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.DataGrip },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on DataGrip.
     *
     * @param version The provider for the version of DataGrip.
     */
    fun datagrip(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.DataGrip },
        versionProvider = version,
    )

    /**
     * Adds a dependency on DataSpell.
     *
     * @param version The version of DataSpell.
     */
    fun dataspell(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.DataSpell },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on DataSpell.
     *
     * @param version The provider for the version of DataSpell.
     */
    fun dataspell(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.DataSpell },
        versionProvider = version,
    )

    /**
     * Adds a dependency on CLion.
     *
     * @param version The version of CLion.
     */
    fun clion(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.CLion },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on CLion.
     *
     * @param version The provider for the version of CLion.
     */
    fun clion(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.CLion },
        versionProvider = version,
    )

    /**
     * Adds a dependency on Fleet Backend.
     *
     * @param version The version of Fleet Backend.
     */
    fun fleetBackend(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.FleetBackend },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on Fleet Backend.
     *
     * @param version The provider for the version of Fleet Backend.
     */
    fun fleetBackend(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.FleetBackend },
        versionProvider = version,
    )

    /**
     * Adds a dependency on Gateway.
     *
     * @param version The version of Gateway.
     */
    fun gateway(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.Gateway },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on Gateway.
     *
     * @param version The provider for the version of Gateway.
     */
    fun gateway(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.Gateway },
        versionProvider = version,
    )

    /**
     * Adds a dependency on GoLand.
     *
     * @param version The version of GoLand.
     */
    fun goland(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.GoLand },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on GoLand.
     *
     * @param version The provider for the version of GoLand.
     */
    fun goland(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.GoLand },
        versionProvider = version,
    )

    /**
     * Adds a dependency on IntelliJ IDEA Community.
     *
     * @param version The version of IntelliJ IDEA Community.
     */
    fun intellijIdeaCommunity(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.IntellijIdeaCommunity },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on IntelliJ IDEA Community.
     *
     * @param version The provider for the version of IntelliJ IDEA Community.
     */
    fun intellijIdeaCommunity(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.IntellijIdeaCommunity },
        versionProvider = version,
    )

    /**
     * Adds a dependency on IntelliJ IDEA Ultimate.
     *
     * @param version The version of IntelliJ IDEA Ultimate.
     */
    fun intellijIdeaUltimate(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.IntellijIdeaUltimate },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on IntelliJ IDEA Ultimate.
     *
     * @param version The provider for the version of IntelliJ IDEA Ultimate.
     */
    fun intellijIdeaUltimate(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.IntellijIdeaUltimate },
        versionProvider = version,
    )

    /**
     * Adds a dependency on MPS.
     *
     * @param version The version of MPS.
     */
    fun mps(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.MPS },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on MPS.
     *
     * @param version The provider for the version of MPS.
     */
    fun mps(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.MPS },
        versionProvider = version,
    )

    /**
     * Adds a dependency on PhpStorm.
     *
     * @param version The version of PhpStorm.
     */
    fun phpstorm(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.PhpStorm },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on PhpStorm.
     *
     * @param version The provider for the version of PhpStorm.
     */
    fun phpstorm(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.PhpStorm },
        versionProvider = version,
    )

    /**
     * Adds a dependency on PyCharm Community.
     *
     * @param version The version of PyCharm Community.
     */
    fun pycharmCommunity(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.PyCharmCommunity },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on PyCharm Community.
     *
     * @param version The provider for the version of PyCharm Community.
     */
    fun pycharmCommunity(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.PyCharmCommunity },
        versionProvider = version,
    )

    /**
     * Adds a dependency on PyCharm Professional.
     *
     * @param version The version of PyCharm Professional.
     */
    fun pycharmProfessional(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.PyCharmProfessional },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on PyCharm Professional.
     *
     * @param version The provider for the version of PyCharm Professional.
     */
    fun pycharmProfessional(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.PyCharmProfessional },
        versionProvider = version,
    )

    /**
     * Adds a dependency on Rider.
     *
     * @param version The version of Rider.
     */
    fun rider(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.Rider },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on Rider.
     *
     * @param version The provider for the version of Rider.
     */
    fun rider(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.Rider },
        versionProvider = version,
    )

    /**
     * Adds a dependency on RubyMine.
     *
     * @param version The version of RubyMine.
     */
    fun rubymine(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.RubyMine },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on RubyMine.
     *
     * @param version The provider for the version of RubyMine.
     */
    fun rubymine(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.RubyMine },
        versionProvider = version,
    )

    /**
     * Adds a dependency on Rust Rover.
     *
     * @param version The version of Rust Rover.
     */
    fun rustRover(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.RustRover },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on Rust Rover.
     *
     * @param version The provider for the version of Rust Rover.
     */
    fun rustRover(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.RustRover },
        versionProvider = version,
    )

    /**
     * Adds a dependency on WebStorm.
     *
     * @param version The version of WebStorm.
     */
    fun webstorm(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.WebStorm },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on WebStorm.
     *
     * @param version The provider for the version of WebStorm.
     */
    fun webstorm(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.WebStorm },
        versionProvider = version,
    )

    /**
     * Adds a dependency on Writerside.
     *
     * @param version The version of Writerside.
     */
    fun writerside(version: String) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.Writerside },
        versionProvider = providers.provider { version },
    )

    /**
     * Adds a dependency on Writerside.
     *
     * @param version The provider for the version of Writerside.
     */
    fun writerside(version: Provider<String>) = addIntelliJPlatformDependency(
        typeProvider = providers.provider { IntelliJPlatformType.Writerside },
        versionProvider = version,
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
        plugins = notation.map { listOfNotNull(it.parsePluginNotation()) }
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: String) = addIntelliJPlatformPluginDependencies(
        plugins = providers.provider { listOfNotNull(notation.parsePluginNotation()) }
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(vararg notations: String) = addIntelliJPlatformPluginDependencies(
        plugins = providers.provider { notations.mapNotNull { it.parsePluginNotation() } }
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: List<String>) = addIntelliJPlatformPluginDependencies(
        plugins = providers.provider { notations.mapNotNull { it.parsePluginNotation() } }
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: Provider<List<String>>) = addIntelliJPlatformPluginDependencies(
        plugins = notations.map { it.mapNotNull { notation -> notation.parsePluginNotation() } }
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
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(ids: Provider<List<String>>) = addIntelliJPlatformBundledPluginDependencies(
        bundledPlugins = ids
    )

    /**
     * Adds a dependency on IntelliJ Plugin Verifier.
     *
     * @param version The IntelliJ Plugin Verifier version.
     */
    fun pluginVerifier(version: String = VERSION_LATEST) = addPluginVerifierDependency(providers.provider { version })

    /**
     * Adds a dependency on IntelliJ Plugin Verifier.
     *
     * @param version The provider of the IntelliJ Plugin Verifier version.
     */
    fun pluginVerifier(version: Provider<String>) = addPluginVerifierDependency(version)

    /**
     * Adds a dependency on Marketplace ZIP Signer.
     *
     * @param version The Marketplace ZIP Signer version.
     */
    fun zipSigner(version: String = VERSION_LATEST) = addZipSignerDependency(providers.provider { version })

    /**
     * Adds a dependency on Marketplace ZIP Signer.
     *
     * @param version The provider of the Marketplace ZIP Signer version.
     */
    fun zipSigner(version: Provider<String>) = addZipSignerDependency(version)

    /**
     * Adds a dependency on the `test-framework` library or its variant, required for testing plugins.
     *
     * There are multiple Test Framework variants available, which provide additional classes for testing specific modules, like:
     * JUnit4, JUnit 5, Maven, JavaScript, Go, Java, ReSharper, etc.
     *
     * The version, if absent, is determined by the IntelliJ Platform build number.
     * If the exact version is unavailable, and the [BuildFeature.USE_CLOSEST_VERSION_RESOLVING] flag is set to `true`,
     * the closest available version is used, found by scanning all releases in the repository.
     *
     * @param type Test framework variant type.
     * @param version Test framework library version.
     * @see TestFrameworkType
     */
    fun testFramework(type: TestFrameworkType, version: String = VERSION_CURRENT) =
        addTestFrameworkDependency(providers.provider { type }, providers.provider { version })

    /**
     * Adds a dependency on the `test-framework` library required for testing plugins.
     *
     * There are multiple Test Framework variants available, which provide additional classes for testing specific modules, like:
     * JUnit4, JUnit 5, Maven, JavaScript, Go, Java, ReSharper, etc.
     *
     * If the exact version is unavailable, and the [BuildFeature.USE_CLOSEST_VERSION_RESOLVING] flag is set to `true`,
     * the closest available version is used, found by scanning all releases in the repository.
     *
     * @param type test framework variant type
     * @param version library version
     * @see TestFrameworkType
     */
    fun testFramework(type: Provider<TestFrameworkType>, version: Provider<String>) = addTestFrameworkDependency(type, version)

    /**
     * Adds a Java Compiler dependency for code instrumentation.
     *
     * By default, the version is determined by the IntelliJ Platform build number.
     *
     * If the exact version is unavailable, and the [BuildFeature.USE_CLOSEST_VERSION_RESOLVING] flag is set to `true`,
     * the closest available version is used, found by scanning all releases in the repository.
     *
     * @param version Java Compiler version
     */
    fun javaCompiler(version: String = VERSION_CURRENT) = addJavaCompilerDependency(providers.provider { version })

    /**
     * Adds a Java Compiler dependency for code instrumentation.
     *
     * By default, the version is determined by the IntelliJ Platform build number.
     *
     * If the exact version is unavailable, and the [BuildFeature.USE_CLOSEST_VERSION_RESOLVING] flag is set to `true`,
     * the closest available version is used, found by scanning all releases in the repository.
     *
     * @param version Java Compiler version
     */
    fun javaCompiler(version: Provider<String>) = addJavaCompilerDependency(version)

    /**
     * Applies a set of dependencies required for running the [InstrumentCodeTask] task.
     * - [javaCompiler] — Java Compiler dependency used for running Ant tasks
     */
    fun instrumentationTools() {
        javaCompiler()
    }

    /**
     * Adds a dependency on a Jar bundled within the current IntelliJ Platform.
     *
     * @param path Path to the bundled Jar file, like `lib/testFramework.jar`
     */
    fun bundledLibrary(path: String) = addBundledLibrary(providers.provider { path })

    /**
     * Adds a dependency on a Jar bundled within the current IntelliJ Platform.
     *
     * @param path Path to the bundled Jar file, like `lib/testFramework.jar`
     */
    fun bundledLibrary(path: Provider<String>) = addBundledLibrary(path)

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
    private fun addIntelliJPlatformDependency(
        typeProvider: Provider<*>,
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        typeProvider.map { it.toIntelliJPlatformType() }.zip(versionProvider) { type, version ->
            requireNotNull(type.dependency) { "Specified type '$type' has no dependency available." }

            when (type) {
                IntelliJPlatformType.AndroidStudio -> {
                    val downloadLink = providers.of(AndroidStudioDownloadLinkValueSource::class) {
                        parameters {
                            androidStudio = resources.resolve(Locations.PRODUCTS_RELEASES_ANDROID_STUDIO)
                            androidStudioVersion = version
                        }
                    }.orNull
                    requireNotNull(downloadLink) { "Couldn't resolve Android Studio download URL for version: $version" }

                    val (classifier, extension) = downloadLink.substringAfter("$version-").split(".", limit = 2)

                    dependencies.create(
                        group = type.dependency.groupId,
                        name = type.dependency.artifactId,
                        classifier = classifier,
                        ext = extension,
                        version = version,
                    )
                }

                else -> dependencies.create(
                    group = type.dependency.groupId,
                    name = type.dependency.artifactId,
                    version = version,
                )
            }.apply(action)
        },
    )

    /**
     * A base method for adding a dependency on a local IntelliJ Platform instance.
     *
     * @param localPathProvider The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action An optional action to be performed on the created dependency.
     * @throws GradleException
     */
    @Throws(GradleException::class)
    private fun addIntelliJPlatformLocalDependency(
        localPathProvider: Provider<*>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_LOCAL_INSTANCE,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        localPathProvider.map { localPath ->
            val artifactPath = resolveArtifactPath(localPath)
            val localProductInfo = artifactPath.productInfo()

            localProductInfo.validateSupportedVersion()

            val hash = artifactPath.hashCode().absoluteValue % 1000
            val type = localProductInfo.productCode.toIntelliJPlatformType()
            val coordinates = type.dependency ?: type.binary
            requireNotNull(coordinates) { "Specified type '$type' has no dependency available." }

            dependencies.create(
                group = Configurations.Dependencies.LOCAL_IDE_GROUP,
                name = coordinates.groupId,
                version = "${localProductInfo.version}+$hash",
            ).apply {
                createIvyDependencyFile(
                    localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
                    publications = listOf(artifactPath.toPublication()),
                )
            }.apply(action)
        },
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
    ) = configurations[configurationName].dependencies.addLater(
        explicitVersionProvider.map {
            dependencies.create(
                group = "com.jetbrains",
                name = "jbr",
                version = it,
                ext = "tar.gz",
            ).apply(action)
        },
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
    ) = configurations[configurationName].dependencies.addAllLater(
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
    ) = configurations[configurationName].dependencies.addAllLater(
        bundledPlugins.map {
            it
                .filter { id -> id.isNotBlank() }
                .map { id -> createIntelliJPlatformBundledPluginDependency(id).apply(action) }
        }
    )

    /**
     * Creates a dependency for an IntelliJ platform bundled plugin.
     *
     * @param bundledPluginId The ID of the bundled plugin.
     * @throws IllegalArgumentException
     */
    @Throws(IllegalArgumentException::class)
    private fun createIntelliJPlatformBundledPluginDependency(bundledPluginId: String): Dependency {
        val id = bundledPluginId.trim()
        val bundledPluginsList = configurations[Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS_LIST].asLenient.single().toPath().bundledPlugins()
        val bundledPlugin = bundledPluginsList.plugins.find { it.id == id }
        requireNotNull(bundledPlugin) { "Could not find bundled plugin with ID: '$id'" }

        val artifactPath = Path(bundledPlugin.path)
        val jars = artifactPath.resolve("lib").listDirectoryEntries("*.jar")
        val hash = artifactPath.hashCode().absoluteValue % 1000

        return dependencies.create(
            group = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
            name = id,
            version = "${productInfo.version}+$hash",
        ).apply {
            createIvyDependencyFile(
                localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
                publications = jars.map { it.toPublication() },
            )
        }
    }

    /**
     * A base method for adding  a dependency on IntelliJ Plugin Verifier.
     *
     * @param versionProvider The provider of the IntelliJ Plugin Verifier version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    private fun addPluginVerifierDependency(
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLUGIN_VERIFIER,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        versionProvider.map { version ->
            dependencies.create(
                group = "org.jetbrains.intellij.plugins",
                name = "verifier-cli",
                version = when (version) {
                    VERSION_LATEST -> IntelliJPluginVerifierLatestVersionResolver().resolve().version
                    else -> version
                },
                classifier = "all",
                ext = "jar",
            ).apply(action)
        },
    )

    /**
     * A base method for adding a dependency on Marketplace ZIP Signer.
     *
     * @param versionProvider The provider of the Marketplace ZIP Signer version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    private fun addZipSignerDependency(
        versionProvider: Provider<String>,
        configurationName: String = Configurations.MARKETPLACE_ZIP_SIGNER,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        versionProvider.map { version ->
            dependencies.create(
                group = "org.jetbrains",
                name = "marketplace-zip-signer",
                version = when (version) {
                    VERSION_LATEST -> MarketplaceZipSignerLatestVersionResolver().resolve().version
                    else -> version
                },
                classifier = "cli",
                ext = "jar",
            ).apply(action)
        },
    )

    /**
     * Adds a dependency on a Java Compiler used, i.e., for running code instrumentation.
     *
     * @param versionProvider The provider of the Java Compiler version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    private fun addJavaCompilerDependency(
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_JAVA_COMPILER,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        versionProvider.map { version ->
            val resolveClosest = BuildFeature.USE_CLOSEST_VERSION_RESOLVING.getValue(providers).get()

            dependencies.create(
                group = "com.jetbrains.intellij.java",
                name = "java-compiler-ant-tasks",
                version = when (version) {
                    VERSION_CURRENT -> when {
                        resolveClosest -> JavaCompilerClosestVersionResolver(
                            productInfo,
                            repositories.urls(),
                        ).resolve().version

                        else -> productInfo.buildNumber
                    }

                    else -> version
                },
            ).apply(action)
        }
    )

    /**
     * Adds a dependency on the `test-framework` library required for testing plugins.
     *
     * In rare cases, when the presence of bundled `lib/testFramework.jar` library is necessary,
     * it is possible to attach it by using the [TestFrameworkType.Platform.Bundled] type.
     *
     * This dependency belongs to IntelliJ Platform repositories.
     *
     * @param typeProvider The TestFramework type provider.
     * @param versionProvider The version of the TestFramework.
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    private fun addTestFrameworkDependency(
        typeProvider: Provider<TestFrameworkType>,
        versionProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        typeProvider.zip(versionProvider) { type, version ->
            when (type) {
                TestFrameworkType.Platform.Bundled -> createBundledLibraryDependency(type.coordinates.artifactId)

                else -> {
                    val resolveClosest = BuildFeature.USE_CLOSEST_VERSION_RESOLVING.getValue(providers).get()

                    val moduleDescriptors = providers.of(ModuleDescriptorsValueSource::class) {
                        parameters {
                            intellijPlatformPath = layout.dir(providers.provider { platformPath.toFile() })
                        }
                    }

                    dependencies.create(
                        group = type.coordinates.groupId,
                        name = type.coordinates.artifactId,
                        version = when (version) {
                            VERSION_CURRENT -> when {
                                resolveClosest -> TestFrameworkClosestVersionResolver(
                                    productInfo,
                                    repositories.urls(),
                                    type.coordinates,
                                ).resolve().version

                                else -> productInfo.buildNumber
                            }

                            else -> version
                        }
                    ).apply {
                        moduleDescriptors.get().forEach {
                            exclude(it.groupId, it.artifactId)
                        }
                    }
                }
            }.apply(action)
        },
    )

    /**
     * Adds a dependency on a Jar bundled within the current IntelliJ Platform.
     *
     * @param pathProvider Path to the bundled Jar file, like `lib/testFramework.jar`
     * @param configurationName The name of the configuration to add the dependency to.
     * @param action The action to be performed on the dependency. Defaults to an empty action.
     */
    private fun addBundledLibrary(
        pathProvider: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
        action: DependencyAction = {},
    ) = configurations[configurationName].dependencies.addLater(
        pathProvider.map { path -> createBundledLibraryDependency(path).apply(action) }
    )

    /**
     * Creates a [Dependency] using a Jar file resolved in [platformPath] with [path].
     */
    private fun createBundledLibraryDependency(path: String) = dependencies.create(objects.fileCollection().from(platformPath.resolve(path)))

    /**
     * Retrieves URLs from registered repositories.
     */
    private fun RepositoryHandler.urls() = mapNotNull { (it as? UrlArtifactRepository)?.url?.toString() }
}

/**
 * Parses the plugin notation into the `<id, version, channel` triple.
 *
 * Possible notations are `id:version` or `id:version@channel`.
 */
private fun String.parsePluginNotation() = trim()
    .takeIf { it.isNotEmpty() }
    ?.split(":", "@")
    ?.run { Triple(getOrNull(0).orEmpty(), getOrNull(1).orEmpty(), getOrNull(2).orEmpty()) }

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
 * Definition of Test Framework types available for writing tests for IntelliJ Platform plugins.
 */
interface TestFrameworkType {

    /**
     * Maven coordinates of test framework artifact;
     */
    val coordinates: Coordinates;

    enum class Platform(override val coordinates: Coordinates) : TestFrameworkType {
        JUnit4(Coordinates("com.jetbrains.intellij.platform", "test-framework")),
        JUnit5(Coordinates("com.jetbrains.intellij.platform", "test-framework-junit5")),
        Bundled(Coordinates("bundled", "lib/testFramework.jar")),
    }

    enum class Plugin(override val coordinates: Coordinates) : TestFrameworkType {
        Go(Coordinates("com.jetbrains.intellij.go", "go-test-framework")),
        Ruby(Coordinates("com.jetbrains.intellij.idea", "ruby-test-framework")),
        Java(Coordinates("com.jetbrains.intellij.java", "java-test-framework")),
        JavaScript(Coordinates("com.jetbrains.intellij.javascript", "javascript-test-framework")),
        Maven(Coordinates("com.jetbrains.intellij.maven", "maven-test-framework")),
        ReSharper(Coordinates("com.jetbrains.intellij.resharper", "resharper-test-framework")),
    }
}

/**
 * Type alias for a lambda function that takes a [Dependency] and performs some actions on it.
 */
internal typealias DependencyAction = (Dependency.() -> Unit)
