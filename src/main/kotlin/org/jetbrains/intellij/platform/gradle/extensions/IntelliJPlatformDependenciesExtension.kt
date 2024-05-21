// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ProjectDependency
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
import org.jetbrains.intellij.platform.gradle.extensions.helpers.*
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
    configurations: ConfigurationContainer,
    repositories: RepositoryHandler,
    settingsRepositories: RepositoryHandler,
    dependencies: DependencyHandler,
    providers: ProviderFactory,
    resources: ResourceHandler,
    objects: ObjectFactory,
    layout: ProjectLayout,
    rootProjectDirectory: Path,
) {

    private val providersDelegate = ProvidersHelper(providers)

    private val intelliJPlatformDelegate = IntelliJPlatformHelper(
        configurations,
        providers,
    )

    private val bundledLibraryDelegate = BundledLibraryHelper(
        configurations,
        dependencies,
        objects,
        intelliJPlatformDelegate.platformPath,
    )

    private val intelliJPlatformDependencyDelegate = IntelliJPlatformDependencyHelper(
        configurations,
        dependencies,
        providers,
        resources,
        rootProjectDirectory,
    )

    private val intelliJPlatformPluginDependencyDelegate = IntelliJPlatformPluginDependencyHelper(
        configurations,
        dependencies,
        intelliJPlatformDelegate.platformPath,
        intelliJPlatformDelegate.productInfo,
        providers,
        rootProjectDirectory
    )

    private val javaCompilerDependencyDelegate = JavaCompilerDependencyHelper(
        configurations,
        dependencies,
        intelliJPlatformDelegate.productInfo,
        providers,
        repositories,
        settingsRepositories,
    )

    private val jetBrainsRuntimeDependencyDelegate = JetBrainsRuntimeDependencyHelper(
        configurations,
        dependencies,
        intelliJPlatformDelegate.platformPath,
        providers,
    )

    private val pluginVerifierDependencyDelegate = PluginVerifierDependencyHelper(
        configurations,
        dependencies,
    )

    private val testFrameworkDependencyDelegate = TestFrameworkDependencyHelper(
        configurations,
        dependencies,
        layout,
        objects,
        intelliJPlatformDelegate.platformPath,
        intelliJPlatformDelegate.productInfo,
        providers,
        repositories,
        settingsRepositories,
    )

    private val zipSignerDependencyDelegate = ZipSignerDependencyHelper(
        configurations,
        dependencies,
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The version of the IntelliJ Platform dependency.
     */
    fun create(type: String, version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { type.toIntelliJPlatformType() },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param version The version of the IntelliJ Platform dependency.
     */
    fun create(type: Provider<*>, version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = type,
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The version of the IntelliJ Platform dependency.
     */
    fun create(type: IntelliJPlatformType, version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { type },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The provider for the version of the IntelliJ Platform dependency.
     */
    fun create(type: String, version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { type.toIntelliJPlatformType() },
            versionProvider = version,
        )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The provider for the version of the IntelliJ Platform dependency.
     */
    fun create(type: IntelliJPlatformType, version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { type },
            versionProvider = version,
        )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param version The provider for the version of the IntelliJ Platform dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     */
    fun create(type: Provider<*>, version: Provider<String>, configurationName: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
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
    fun create(type: Provider<*>, version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = type,
            versionProvider = version,
        )

    /**
     * Adds a dependency on Android Studio.
     *
     * @param version The version of Android Studio.
     */
    fun androidStudio(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.AndroidStudio },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on Android Studio.
     *
     * @param version The provider for the version of Android Studio.
     */
    fun androidStudio(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.AndroidStudio },
            versionProvider = version,
        )

    /**
     * Adds a dependency on Aqua.
     *
     * @param version The version of Aqua.
     */
    fun aqua(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.Aqua },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on Aqua.
     *
     * @param version The provider for the version of Aqua.
     */
    fun aqua(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.Aqua },
            versionProvider = version,
        )

    /**
     * Adds a dependency on DataGrip.
     *
     * @param version The version of DataGrip.
     */
    fun datagrip(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.DataGrip },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on DataGrip.
     *
     * @param version The provider for the version of DataGrip.
     */
    fun datagrip(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.DataGrip },
            versionProvider = version,
        )

    /**
     * Adds a dependency on DataSpell.
     *
     * @param version The version of DataSpell.
     */
    fun dataspell(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.DataSpell },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on DataSpell.
     *
     * @param version The provider for the version of DataSpell.
     */
    fun dataspell(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.DataSpell },
            versionProvider = version,
        )

    /**
     * Adds a dependency on CLion.
     *
     * @param version The version of CLion.
     */
    fun clion(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.CLion },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on CLion.
     *
     * @param version The provider for the version of CLion.
     */
    fun clion(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.CLion },
            versionProvider = version,
        )

    /**
     * Adds a dependency on Fleet Backend.
     *
     * @param version The version of Fleet Backend.
     */
    fun fleetBackend(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.FleetBackend },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on Fleet Backend.
     *
     * @param version The provider for the version of Fleet Backend.
     */
    fun fleetBackend(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.FleetBackend },
            versionProvider = version,
        )

    /**
     * Adds a dependency on Gateway.
     *
     * @param version The version of Gateway.
     */
    fun gateway(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.Gateway },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on Gateway.
     *
     * @param version The provider for the version of Gateway.
     */
    fun gateway(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.Gateway },
            versionProvider = version,
        )

    /**
     * Adds a dependency on GoLand.
     *
     * @param version The version of GoLand.
     */
    fun goland(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.GoLand },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on GoLand.
     *
     * @param version The provider for the version of GoLand.
     */
    fun goland(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.GoLand },
            versionProvider = version,
        )

    /**
     * Adds a dependency on IntelliJ IDEA Community.
     *
     * @param version The version of IntelliJ IDEA Community.
     */
    fun intellijIdeaCommunity(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.IntellijIdeaCommunity },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on IntelliJ IDEA Community.
     *
     * @param version The provider for the version of IntelliJ IDEA Community.
     */
    fun intellijIdeaCommunity(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.IntellijIdeaCommunity },
            versionProvider = version,
        )

    /**
     * Adds a dependency on IntelliJ IDEA Ultimate.
     *
     * @param version The version of IntelliJ IDEA Ultimate.
     */
    fun intellijIdeaUltimate(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.IntellijIdeaUltimate },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on IntelliJ IDEA Ultimate.
     *
     * @param version The provider for the version of IntelliJ IDEA Ultimate.
     */
    fun intellijIdeaUltimate(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.IntellijIdeaUltimate },
            versionProvider = version,
        )

    /**
     * Adds a dependency on MPS.
     *
     * @param version The version of MPS.
     */
    fun mps(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.MPS },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on MPS.
     *
     * @param version The provider for the version of MPS.
     */
    fun mps(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.MPS },
            versionProvider = version,
        )

    /**
     * Adds a dependency on PhpStorm.
     *
     * @param version The version of PhpStorm.
     */
    fun phpstorm(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.PhpStorm },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on PhpStorm.
     *
     * @param version The provider for the version of PhpStorm.
     */
    fun phpstorm(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.PhpStorm },
            versionProvider = version,
        )

    /**
     * Adds a dependency on PyCharm Community.
     *
     * @param version The version of PyCharm Community.
     */
    fun pycharmCommunity(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.PyCharmCommunity },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on PyCharm Community.
     *
     * @param version The provider for the version of PyCharm Community.
     */
    fun pycharmCommunity(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.PyCharmCommunity },
            versionProvider = version,
        )

    /**
     * Adds a dependency on PyCharm Professional.
     *
     * @param version The version of PyCharm Professional.
     */
    fun pycharmProfessional(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.PyCharmProfessional },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on PyCharm Professional.
     *
     * @param version The provider for the version of PyCharm Professional.
     */
    fun pycharmProfessional(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.PyCharmProfessional },
            versionProvider = version,
        )

    /**
     * Adds a dependency on Rider.
     *
     * @param version The version of Rider.
     */
    fun rider(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.Rider },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on Rider.
     *
     * @param version The provider for the version of Rider.
     */
    fun rider(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.Rider },
            versionProvider = version,
        )

    /**
     * Adds a dependency on RubyMine.
     *
     * @param version The version of RubyMine.
     */
    fun rubymine(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.RubyMine },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on RubyMine.
     *
     * @param version The provider for the version of RubyMine.
     */
    fun rubymine(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.RubyMine },
            versionProvider = version,
        )

    /**
     * Adds a dependency on Rust Rover.
     *
     * @param version The version of Rust Rover.
     */
    fun rustRover(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.RustRover },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on Rust Rover.
     *
     * @param version The provider for the version of Rust Rover.
     */
    fun rustRover(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.RustRover },
            versionProvider = version,
        )

    /**
     * Adds a dependency on WebStorm.
     *
     * @param version The version of WebStorm.
     */
    fun webstorm(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.WebStorm },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on WebStorm.
     *
     * @param version The provider for the version of WebStorm.
     */
    fun webstorm(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.WebStorm },
            versionProvider = version,
        )

    /**
     * Adds a dependency on Writerside.
     *
     * @param version The version of Writerside.
     */
    fun writerside(version: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.Writerside },
            versionProvider = providersDelegate.of { version },
        )

    /**
     * Adds a dependency on Writerside.
     *
     * @param version The provider for the version of Writerside.
     */
    fun writerside(version: Provider<String>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformDependency(
            typeProvider = providersDelegate.of { IntelliJPlatformType.Writerside },
            versionProvider = version,
        )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The local path of the IntelliJ Platform dependency
     */
    fun local(localPath: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformLocalDependency(
            localPathProvider = providersDelegate.of { localPath },
        )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The local path of the IntelliJ Platform dependency
     */
    fun local(localPath: File) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformLocalDependency(
            localPathProvider = providersDelegate.of { localPath },
        )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The local path of the IntelliJ Platform dependency
     */
    fun local(localPath: Directory) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformLocalDependency(
            localPathProvider = providersDelegate.of { localPath },
        )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     */
    fun local(localPath: Provider<*>) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformLocalDependency(
            localPathProvider = localPath,
        )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     * @param configurationName The name of the configuration to add the dependency to.
     */
    fun local(localPath: Provider<*>, configurationName: String) =
        intelliJPlatformDependencyDelegate.addIntelliJPlatformLocalDependency(
            localPathProvider = localPath,
            configurationName = configurationName,
        )

    /**
     * Adds a dependency on JetBrains Runtime.
     * The version is calculated using the current IntelliJ Platform.
     */
    fun jetbrainsRuntime() =
        jetBrainsRuntimeDependencyDelegate.addObtainedJetBrainsRuntimeDependency()

    /**
     * Adds a dependency on JetBrains Runtime.
     *
     * @param explicitVersion The explicit version of the JetBrains Runtime.
     */
    fun jetbrainsRuntimeExplicit(explicitVersion: String) =
        jetBrainsRuntimeDependencyDelegate.addJetBrainsRuntimeDependency(
            explicitVersionProvider = providersDelegate.of { explicitVersion },
        )

    /**
     * Adds a dependency on JetBrains Runtime.
     *
     * @param explicitVersion The provider for the explicit version of the JetBrains Runtime.
     */
    fun jetbrainsRuntimeExplicit(explicitVersion: Provider<String>) =
        jetBrainsRuntimeDependencyDelegate.addJetBrainsRuntimeDependency(
            explicitVersionProvider = explicitVersion,
        )

    /**
     * Adds a local dependency on JetBrains Runtime.
     *
     * @param version The JetBrains Runtime version.
     * @param variant The JetBrains Runtime variant.
     * @param architecture The JetBrains Runtime architecture.
     */
    fun jetbrainsRuntime(version: String, variant: String? = null, architecture: String? = null) =
        jetBrainsRuntimeDependencyDelegate.addJetBrainsRuntimeDependency(
            explicitVersionProvider = providersDelegate.of {
                jetBrainsRuntimeDependencyDelegate.buildJetBrainsRuntimeVersion(
                    version,
                    variant,
                    architecture,
                )
            },
        )

    /**
     * Adds a local dependency on JetBrains Runtime.
     *
     * @param version The provider of the JetBrains Runtime version.
     * @param variant The provider of the JetBrains Runtime variant.
     * @param architecture The provider of the JetBrains Runtime architecture.
     */
    fun jetbrainsRuntime(version: Provider<String>, variant: Provider<String>, architecture: Provider<String>) =
        jetBrainsRuntimeDependencyDelegate.addJetBrainsRuntimeDependency(
            explicitVersionProvider = providersDelegate.of {
                jetBrainsRuntimeDependencyDelegate.buildJetBrainsRuntimeVersion(
                    version.get(),
                    variant.orNull,
                    architecture.orNull
                )
            },
        )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The plugin identifier.
     * @param version The plugin version.
     * @param channel The plugin distribution channel.
     */
    fun plugin(id: String, version: String, channel: String = "") =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
            pluginsProvider = providersDelegate.of { listOf(Triple(id, version, channel)) }
        )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The provider of the plugin identifier.
     * @param version The provider of the plugin version.
     * @param channel The provider of the plugin distribution channel.
     */
    fun plugin(id: Provider<String>, version: Provider<String>, channel: Provider<String>) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
            pluginsProvider = providersDelegate.of { listOf(Triple(id.get(), version.get(), channel.get())) }
        )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: Provider<String>) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
            pluginsProvider = notation.map { listOfNotNull(it.parsePluginNotation()) }
        )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: String) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
            pluginsProvider = providersDelegate.of { listOfNotNull(notation.parsePluginNotation()) }
        )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(vararg notations: String) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
            pluginsProvider = providersDelegate.of { notations.mapNotNull { it.parsePluginNotation() } }
        )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: List<String>) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
            pluginsProvider = providersDelegate.of { notations.mapNotNull { it.parsePluginNotation() } }
        )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: Provider<List<String>>) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
            pluginsProvider = notations.map { it.mapNotNull { notation -> notation.parsePluginNotation() } }
        )

    /**
     * Adds a dependency on a bundled IntelliJ Platform plugin.
     *
     * @param id The bundled plugin identifier.
     */
    fun bundledPlugin(id: String) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = providersDelegate.of { listOf(id) }
        )

    /**
     * Adds a dependency on a bundled IntelliJ Platform plugin.
     *
     * @param id The provider of the bundled plugin identifier.
     */
    fun bundledPlugin(id: Provider<String>) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = id.map { listOf(it) }
        )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(vararg ids: String) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = providersDelegate.of { ids.asList() }
        )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(ids: List<String>) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = providersDelegate.of { ids }
        )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(ids: Provider<List<String>>) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = ids
        )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: File) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformLocalPluginDependency(
            localPathProvider = providersDelegate.of { localPath }
        )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: String) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformLocalPluginDependency(
            localPathProvider = providersDelegate.of { localPath }
        )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Directory) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformLocalPluginDependency(
            localPathProvider = providersDelegate.of { localPath }
        )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Provider<*>) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformLocalPluginDependency(
            localPathProvider = localPath
        )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param dependency Project dependency.
     */
    fun localPlugin(dependency: ProjectDependency) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformLocalPluginProjectDependency(
            dependency = dependency
        )

    /**
     * Adds a dependency on IntelliJ Plugin Verifier.
     *
     * @param version The IntelliJ Plugin Verifier version.
     */
    fun pluginVerifier(version: String = VERSION_LATEST) =
        pluginVerifierDependencyDelegate.addPluginVerifierDependency(providersDelegate.of { version })

    /**
     * Adds a dependency on IntelliJ Plugin Verifier.
     *
     * @param version The provider of the IntelliJ Plugin Verifier version.
     */
    fun pluginVerifier(version: Provider<String>) =
        pluginVerifierDependencyDelegate.addPluginVerifierDependency(version)

    /**
     * Adds a dependency on Marketplace ZIP Signer.
     *
     * @param version The Marketplace ZIP Signer version.
     */
    fun zipSigner(version: String = VERSION_LATEST) =
        zipSignerDependencyDelegate.addZipSignerDependency(providersDelegate.of { version })

    /**
     * Adds a dependency on Marketplace ZIP Signer.
     *
     * @param version The provider of the Marketplace ZIP Signer version.
     */
    fun zipSigner(version: Provider<String>) =
        zipSignerDependencyDelegate.addZipSignerDependency(version)

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
        testFrameworkDependencyDelegate.addTestFrameworkDependency(providersDelegate.of { type }, providersDelegate.of { version })

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
    fun testFramework(type: Provider<TestFrameworkType>, version: Provider<String>) =
        testFrameworkDependencyDelegate.addTestFrameworkDependency(type, version)

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
    fun javaCompiler(version: String = VERSION_CURRENT) =
        javaCompilerDependencyDelegate.addJavaCompilerDependency(providersDelegate.of { version })

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
    fun javaCompiler(version: Provider<String>) =
        javaCompilerDependencyDelegate.addJavaCompilerDependency(version)

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
    fun bundledLibrary(path: String) =
        bundledLibraryDelegate.addBundledLibrary(providersDelegate.of { path })

    /**
     * Adds a dependency on a Jar bundled within the current IntelliJ Platform.
     *
     * @param path Path to the bundled Jar file, like `lib/testFramework.jar`
     */
    fun bundledLibrary(path: Provider<String>) =
        bundledLibraryDelegate.addBundledLibrary(path)
}
