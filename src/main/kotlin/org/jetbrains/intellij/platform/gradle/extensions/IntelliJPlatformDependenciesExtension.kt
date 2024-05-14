// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.VERSION_CURRENT
import org.jetbrains.intellij.platform.gradle.Constants.VERSION_LATEST
import org.jetbrains.intellij.platform.gradle.extensions.aware.*
import org.jetbrains.intellij.platform.gradle.tasks.InstrumentCodeTask
import java.io.File
import java.nio.file.Path
import javax.inject.Inject

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
    override val configurations: ConfigurationContainer,
    override val repositories: RepositoryHandler,
    override val settingsRepositories: RepositoryHandler,
    override val dependencies: DependencyHandler,
    override val providers: ProviderFactory,
    override val resources: ResourceHandler,
    override val objects: ObjectFactory,
    override val layout: ProjectLayout,
    override val rootProjectDirectory: Path,
) : BundledLibraryAware,
    IntelliJPlatformAware,
    IntelliJPlatformDependencyAware,
    IntelliJPlatformPluginDependencyAware,
    JavaCompilerDependencyAware,
    JetBrainsRuntimeDependencyAware,
    PluginVerifierDependencyAware,
    TestFrameworkDependencyAware,
    ZipSignerDependencyAware {

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
     * The version is calculated using the current IntelliJ Platform.
     */
    fun jetbrainsRuntime() = addObtainedJetBrainsRuntimeDependency()

    /**
     * Adds a dependency on JetBrains Runtime.
     *
     * @param explicitVersion The explicit version of the JetBrains Runtime.
     */
    fun jetbrainsRuntimeExplicit(explicitVersion: String) = addJetBrainsRuntimeDependency(
        explicitVersionProvider = providers.provider { explicitVersion },
    )

    /**
     * Adds a dependency on JetBrains Runtime.
     *
     * @param explicitVersion The provider for the explicit version of the JetBrains Runtime.
     */
    fun jetbrainsRuntimeExplicit(explicitVersion: Provider<String>) = addJetBrainsRuntimeDependency(
        explicitVersionProvider = explicitVersion,
    )

    /**
     * Adds a local dependency on JetBrains Runtime.
     *
     * @param version The JetBrains Runtime version.
     * @param variant The JetBrains Runtime variant.
     * @param architecture The JetBrains Runtime architecture.
     */
    fun jetbrainsRuntime(version: String, variant: String? = null, architecture: String? = null) = addJetBrainsRuntimeDependency(
        explicitVersionProvider = providers.provider { buildJetBrainsRuntimeVersion(version, variant, architecture) },
    )

    /**
     * Adds a local dependency on JetBrains Runtime.
     *
     * @param version The provider of the JetBrains Runtime version.
     * @param variant The provider of the JetBrains Runtime variant.
     * @param architecture The provider of the JetBrains Runtime architecture.
     */
    fun jetbrainsRuntime(version: Provider<String>, variant: Provider<String>, architecture: Provider<String>) = addJetBrainsRuntimeDependency(
        explicitVersionProvider = providers.provider { buildJetBrainsRuntimeVersion(version.get(), variant.orNull, architecture.orNull) },
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
}
