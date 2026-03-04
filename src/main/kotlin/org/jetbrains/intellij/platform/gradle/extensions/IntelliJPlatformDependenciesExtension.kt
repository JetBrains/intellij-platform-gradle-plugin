// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.newInstance
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Dependencies
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.Constants.Extensions
import org.jetbrains.intellij.platform.gradle.IntelliJPlatform
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.coroutines
import org.jetbrains.intellij.platform.gradle.models.kotlinStdlib
import org.jetbrains.intellij.platform.gradle.plugins.configureExtension
import org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.zip
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
 * @param dependenciesHelper IntelliJ Platform dependencies helper instance
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@IntelliJPlatform
abstract class IntelliJPlatformDependenciesExtension @Inject constructor(
    private val dependenciesHelper: IntelliJPlatformDependenciesHelper,
    private val extensionProvider: Provider<IntelliJPlatformExtension>,
    private val objects: ObjectFactory,
) {

    /**
     * Creates and configures an instance of [IntelliJPlatformDependencyConfiguration] and
     * adds an IntelliJ Platform dependency based on the provided configuration.
     *
     * @param configure IntelliJ Platform dependency configuration lambda.
     */
    fun create(configure: Action<IntelliJPlatformDependencyConfiguration>) =
        create(
            objects
                .newInstance<IntelliJPlatformDependencyConfiguration>(objects, extensionProvider)
                .apply(configure::execute),
        )

    /**
     * Creates and configures an instance of [IntelliJPlatformDependencyConfiguration] and
     * adds an IntelliJ Platform dependency based on the provided configuration.
     *
     * @param configure IntelliJ Platform dependency configuration.
     */
    fun create(configuration: IntelliJPlatformDependencyConfiguration) =
        create(listOf(configuration))

    /**
     * Creates and configures an instance of [IntelliJPlatformDependencyConfiguration] and
     * adds an IntelliJ Platform dependency based on the provided configuration.
     *
     * @param configure IntelliJ Platform dependency configuration.
     */
    internal fun create(
        configurations: List<IntelliJPlatformDependencyConfiguration>,
        dependencyConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
        dependencyArchivesConfigurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCY_ARCHIVE,
        localArchivesConfigurationName: String = Configurations.INTELLIJ_PLATFORM_LOCAL,
    ) =
        dependenciesHelper.addIntelliJPlatformCacheableDependencies(
            configurationsProvider = dependenciesHelper.provider { configurations },
            dependencyConfigurationName = dependencyConfigurationName,
            dependencyArchivesConfigurationName = dependencyArchivesConfigurationName,
            localArchivesConfigurationName = localArchivesConfigurationName,
        )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The version of the IntelliJ Platform dependency.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun create(
        type: Any,
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create {
        this.type = type.toIntelliJPlatformType(version)
        this.version = version
        apply(configure::execute)
    }

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The version of the IntelliJ Platform dependency.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun create(
        type: Any,
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create {
        this.type = version.map { type.toIntelliJPlatformType(it) }
        this.version = version
        apply(configure::execute)
    }

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The version of the IntelliJ Platform dependency.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun create(
        type: Provider<*>,
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create {
        this.type = type.zip(version) { typeValue, versionValue -> typeValue.toIntelliJPlatformType(versionValue) }
        this.version = version
        apply(configure::execute)
    }

    /**
     * Adds a dependency on Android Studio.
     *
     * @param version The version of Android Studio.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun androidStudio(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.AndroidStudio, version, configure)

    /**
     * Adds a dependency on Android Studio.
     *
     * @param version The version of Android Studio.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun androidStudio(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.AndroidStudio, version, configure)

    /**
     * Adds a dependency on DataGrip.
     *
     * @param version The version of DataGrip.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun datagrip(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.DataGrip, version, configure)

    /**
     * Adds a dependency on DataGrip.
     *
     * @param version The version of DataGrip.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun datagrip(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.DataGrip, version, configure)

    /**
     * Adds a dependency on DataSpell.
     *
     * @param version The version of DataSpell.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun dataspell(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.DataSpell, version, configure)

    /**
     * Adds a dependency on DataSpell.
     *
     * @param version The version of DataSpell.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun dataspell(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.DataSpell, version, configure)

    /**
     * Adds a dependency on CLion.
     *
     * @param version The version of CLion.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun clion(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.CLion, version, configure)

    /**
     * Adds a dependency on CLion.
     *
     * @param version The version of CLion.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun clion(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.CLion, version, configure)

    /**
     * Adds a dependency on Fleet Backend.
     *
     * @param version The version of Fleet Backend.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun fleetBackend(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.FleetBackend, version, configure)

    /**
     * Adds a dependency on Fleet Backend.
     *
     * @param version The version of Fleet Backend.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun fleetBackend(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.FleetBackend, version, configure)

    /**
     * Adds a dependency on Gateway.
     *
     * @param version The version of Gateway.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun gateway(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.Gateway, version, configure)

    /**
     * Adds a dependency on Gateway.
     *
     * @param version The version of Gateway.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun gateway(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.Gateway, version, configure)

    /**
     * Adds a dependency on GoLand.
     *
     * @param version The version of GoLand.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun goland(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.GoLand, version, configure)

    /**
     * Adds a dependency on GoLand.
     *
     * @param version The version of GoLand.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun goland(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.GoLand, version, configure)

    /**
     * Adds a dependency on IntelliJ IDEA Community.
     *
     * @param version The version of IntelliJ IDEA.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun intellijIdea(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.IntellijIdea, version, configure)

    /**
     * Adds a dependency on IntelliJ IDEA Community.
     *
     * @param version The version of IntelliJ IDEA Community.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun intellijIdea(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.IntellijIdea, version, configure)

    /**
     * Adds a dependency on IntelliJ IDEA Community.
     *
     * Note: Starting with version 2025.3, IntelliJ IDEA Community (IC) is no longer published.
     * For versions 2025.3+, use [intellijIdea] instead.
     *
     * @param version The version of IntelliJ IDEA Community.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun intellijIdeaCommunity(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.IntellijIdeaCommunity, version, configure)

    /**
     * Adds a dependency on IntelliJ IDEA Community.
     *
     * Note: Starting with version 2025.3, IntelliJ IDEA Community (IC) is no longer published.
     * For versions 2025.3+, use [intellijIdea] instead.
     *
     * @param version The version of IntelliJ IDEA Community.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun intellijIdeaCommunity(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.IntellijIdeaCommunity, version, configure)

    /**
     * Adds a dependency on IntelliJ IDEA Ultimate.
     *
     * Note: Starting with version 2025.3, use [intellijIdea] instead which supports both Community and Ultimate editions.
     * This method is still valid for versions prior to 2025.3.
     *
     * @param version The version of IntelliJ IDEA Ultimate.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun intellijIdeaUltimate(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.IntellijIdeaUltimate, version, configure)

    /**
     * Adds a dependency on IntelliJ IDEA Ultimate.
     *
     * Note: Starting with version 2025.3, use [intellijIdea] instead which supports both Community and Ultimate editions.
     * This method is still valid for versions prior to 2025.3.
     *
     * @param version The version of IntelliJ IDEA Ultimate.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun intellijIdeaUltimate(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.IntellijIdeaUltimate, version, configure)

    /**
     * Adds a dependency on MPS.
     *
     * @param version The version of MPS.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun mps(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.MPS, version, configure)

    /**
     * Adds a dependency on MPS.
     *
     * @param version The version of MPS.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun mps(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.MPS, version, configure)

    /**
     * Adds a dependency on PhpStorm.
     *
     * @param version The version of PhpStorm.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun phpstorm(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.PhpStorm, version, configure)

    /**
     * Adds a dependency on PhpStorm.
     *
     * @param version The version of PhpStorm.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun phpstorm(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.PhpStorm, version, configure)

    /**
     * Adds a dependency on PyCharm.
     *
     * @param version The version of PyCharm.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun pycharm(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.PyCharm, version, configure)

    /**
     * Adds a dependency on PyCharm.
     *
     * @param version The version of PyCharm.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun pycharm(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.PyCharm, version, configure)

    /**
     * Adds a dependency on PyCharm Community.
     *
     * Note: Starting with version 2025.3, PyCharm Community (PC) is no longer published.
     * For versions 2025.3+, use [pycharm] instead.
     *
     * @param version The version of PyCharm Community.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun pycharmCommunity(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.PyCharmCommunity, version, configure)

    /**
     * Adds a dependency on PyCharm Community.
     *
     * Note: Starting with version 2025.3, PyCharm Community (PC) is no longer published.
     * For versions 2025.3+, use [pycharm] instead.
     *
     * @param version The version of PyCharm Community.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun pycharmCommunity(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.PyCharmCommunity, version, configure)

    /**
     * Adds a dependency on PyCharm Professional.
     *
     * Note: Starting with version 2025.3, PyCharm Professional (PY) is no longer published.
     * For versions 2025.3+, use [pycharm] instead.
     *
     * @param version The version of PyCharm Professional.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun pycharmProfessional(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.PyCharmProfessional, version, configure)

    /**
     * Adds a dependency on PyCharm Professional.
     *
     * Note: Starting with version 2025.3, PyCharm Professional (PC) is no longer published.
     * For versions 2025.3+, use [pycharm] instead.
     *
     * @param version The version of PyCharm Professional.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun pycharmProfessional(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.PyCharmProfessional, version, configure)

    /**
     * Adds a dependency on Rider.
     *
     * @param version The version of Rider.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun rider(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.Rider, version, configure)

    /**
     * Adds a dependency on Rider.
     *
     * @param version The version of Rider.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun rider(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.Rider, version, configure)

    /**
     * Adds a dependency on RubyMine.
     *
     * @param version The version of RubyMine.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun rubymine(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.RubyMine, version, configure)

    /**
     * Adds a dependency on RubyMine.
     *
     * @param version The version of RubyMine.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun rubymine(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.RubyMine, version, configure)

    /**
     * Adds a dependency on Rust Rover.
     *
     * @param version The version of Rust Rover.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun rustRover(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.RustRover, version, configure)

    /**
     * Adds a dependency on Rust Rover.
     *
     * @param version The version of Rust Rover.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun rustRover(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.RustRover, version, configure)

    /**
     * Adds a dependency on WebStorm.
     *
     * @param version The version of WebStorm.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun webstorm(
        version: String,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.WebStorm, version, configure)

    /**
     * Adds a dependency on WebStorm.
     *
     * @param version The version of WebStorm.
     * @param configure IntelliJ Platform dependency configuration.
     */
    @JvmOverloads
    fun webstorm(
        version: Provider<String>,
        configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
    ) = create(IntelliJPlatformType.WebStorm, version, configure)

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The local path of the IntelliJ Platform dependency.
     */
    fun local(localPath: String) =
        dependenciesHelper.addIntelliJPlatformLocalDependency(
            localPathProvider = dependenciesHelper.provider { localPath },
        )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The local path of the IntelliJ Platform dependency.
     */
    fun local(localPath: File) =
        dependenciesHelper.addIntelliJPlatformLocalDependency(
            localPathProvider = dependenciesHelper.provider { localPath },
        )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The local path of the IntelliJ Platform dependency.
     */
    fun local(localPath: Path) =
        dependenciesHelper.addIntelliJPlatformLocalDependency(
            localPathProvider = dependenciesHelper.provider { localPath },
        )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The local path of the IntelliJ Platform dependency.
     */
    fun local(localPath: Directory) =
        dependenciesHelper.addIntelliJPlatformLocalDependency(
            localPathProvider = dependenciesHelper.provider { localPath },
        )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     */
    fun local(localPath: Provider<*>) =
        dependenciesHelper.addIntelliJPlatformLocalDependency(
            localPathProvider = localPath,
        )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     * @param configurationName The name of the configuration to add the dependency to.
     */
    fun local(localPath: Provider<*>, configurationName: String) =
        dependenciesHelper.addIntelliJPlatformLocalDependency(
            localPathProvider = localPath,
            configurationName = configurationName,
        )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     * @param configurationName The name of the configuration to add the dependency to.
     * @param intellijPlatformConfigurationName The name of the IntelliJ Platform configuration that holds information about the current IntelliJ Platform instance.
     */
    internal fun customLocal(
        localPath: Provider<*>,
        configurationName: String,
        intellijPlatformConfigurationName: String,
    ) = dependenciesHelper.addIntelliJPlatformLocalDependency(
        localPathProvider = localPath,
        configurationName = configurationName,
        intellijPlatformConfigurationName = intellijPlatformConfigurationName,
    )

    /**
     * Adds a dependency on JetBrains Runtime.
     * The version is calculated using the current IntelliJ Platform.
     */
    fun jetbrainsRuntime() =
        dependenciesHelper.addJetBrainsRuntimeObtainedDependency()

    /**
     * Adds a dependency on JetBrains Runtime.
     *
     * @param explicitVersion The explicit version of the JetBrains Runtime.
     */
    fun jetbrainsRuntimeExplicit(explicitVersion: String) =
        dependenciesHelper.addJetBrainsRuntimeDependency(
            explicitVersionProvider = dependenciesHelper.provider { explicitVersion },
        )

    /**
     * Adds a dependency on JetBrains Runtime.
     *
     * @param explicitVersion The provider for the explicit version of the JetBrains Runtime.
     */
    fun jetbrainsRuntimeExplicit(explicitVersion: Provider<String>) =
        dependenciesHelper.addJetBrainsRuntimeDependency(
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
        dependenciesHelper.addJetBrainsRuntimeDependency(
            explicitVersionProvider = dependenciesHelper.provider {
                dependenciesHelper.buildJetBrainsRuntimeVersion(version, variant, architecture)
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
        dependenciesHelper.addJetBrainsRuntimeDependency(
            explicitVersionProvider = zip(version, variant, architecture) { version, variant, architecture ->
                dependenciesHelper.buildJetBrainsRuntimeVersion(version, variant, architecture)
            },
        )

    /**
     * Adds a dependency on JetBrains Runtime local instance.
     *
     * @param localPath Path to the local JetBrains Runtime.
     */
    fun jetbrainsRuntimeLocal(localPath: String) =
        dependenciesHelper.addJetBrainsRuntimeLocalDependency(
            localPathProvider = dependenciesHelper.provider { localPath },
        )

    /**
     * Adds a dependency on JetBrains Runtime local instance.
     *
     * @param localPath Path to the local JetBrains Runtime.
     */
    fun jetbrainsRuntimeLocal(localPath: Directory) =
        dependenciesHelper.addJetBrainsRuntimeLocalDependency(
            localPathProvider = dependenciesHelper.provider { localPath },
        )

    /**
     * Adds a dependency on JetBrains Runtime local instance.
     *
     * @param localPath Path to the local JetBrains Runtime.
     */
    fun jetbrainsRuntimeLocal(localPath: Path) =
        dependenciesHelper.addJetBrainsRuntimeLocalDependency(
            localPathProvider = dependenciesHelper.provider { localPath },
        )

    /**
     * Adds a dependency on JetBrains Runtime local instance.
     *
     * @param localPath Path to the local JetBrains Runtime.
     */
    fun jetbrainsRuntimeLocal(localPath: Provider<*>) =
        dependenciesHelper.addJetBrainsRuntimeLocalDependency(
            localPathProvider = localPath,
        )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The plugin identifier.
     * @param version The plugin version.
     * @param group The plugin distribution channel.
     */
    @JvmOverloads
    fun plugin(id: String, version: String, group: String = Dependencies.MARKETPLACE_GROUP) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = dependenciesHelper.provider { listOf(Triple(id, version, group)) },
        )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The provider of the plugin identifier.
     * @param version The provider of the plugin version.
     * @param group The provider of the plugin distribution channel.
     */
    fun plugin(id: Provider<String>, version: Provider<String>, group: Provider<String>) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = dependenciesHelper.provider { listOf(Triple(id.get(), version.get(), group.get())) },
        )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: Provider<String>) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = notation.map { listOfNotNull(it.parsePluginNotation()) },
        )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: String) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = dependenciesHelper.provider { listOfNotNull(notation.parsePluginNotation()) },
        )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(vararg notations: String) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = dependenciesHelper.provider { notations.mapNotNull { it.parsePluginNotation() } },
        )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: List<String>) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = dependenciesHelper.provider { notations.mapNotNull { it.parsePluginNotation() } },
        )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: Provider<List<String>>) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = notations.map { it.mapNotNull { notation -> notation.parsePluginNotation() } },
        )

    /**
     * Adds a dependency on a plugin in a version compatible with the current IntelliJ Platform.
     *
     * @param id The plugin identifier.
     */
    fun compatiblePlugin(id: String) =
        dependenciesHelper.addCompatibleIntelliJPlatformPluginDependencies(
            pluginsProvider = dependenciesHelper.provider { listOf(id) },
        )

    /**
     * Adds a dependency on a plugin in a version compatible with the current IntelliJ Platform.
     *
     * @param id The provider of the plugin identifier.
     */
    fun compatiblePlugin(id: Provider<String>) =
        dependenciesHelper.addCompatibleIntelliJPlatformPluginDependencies(
            pluginsProvider = id.map { listOf(it) },
        )

    /**
     * Adds a dependency on plugins in versions compatible with the current IntelliJ Platform.
     *
     * @param ids The plugin identifiers.
     */
    fun compatiblePlugins(ids: List<String>) =
        dependenciesHelper.addCompatibleIntelliJPlatformPluginDependencies(
            pluginsProvider = dependenciesHelper.provider { ids },
        )

    /**
     * Adds a dependency on plugins in versions compatible with the current IntelliJ Platform.
     *
     * @param ids The plugin identifiers.
     */
    fun compatiblePlugins(vararg ids: String) =
        dependenciesHelper.addCompatibleIntelliJPlatformPluginDependencies(
            pluginsProvider = dependenciesHelper.provider { ids.asList() },
        )

    /**
     * Adds a dependency on plugins in versions compatible with the current IntelliJ Platform.
     *
     * @param ids The provider of the plugin identifiers.
     */
    fun compatiblePlugins(ids: Provider<List<String>>) =
        dependenciesHelper.addCompatibleIntelliJPlatformPluginDependencies(
            pluginsProvider = ids.map { it },
        )

    /**
     * Adds a test dependency on a plugin for IntelliJ Platform.
     *
     * @param id The plugin identifier.
     * @param version The plugin version.
     * @param group The plugin distribution channel.
     */
    @JvmOverloads
    fun testPlugin(id: String, version: String, group: String = Dependencies.MARKETPLACE_GROUP) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = dependenciesHelper.provider { listOf(Triple(id, version, group)) },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_DEPENDENCY,
        )

    /**
     * Adds a test dependency on a plugin for IntelliJ Platform.
     *
     * @param id The provider of the plugin identifier.
     * @param version The provider of the plugin version.
     * @param group The provider of the plugin distribution channel.
     */
    fun testPlugin(id: Provider<String>, version: Provider<String>, group: Provider<String>) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = zip(id, version, group) { id, version, group -> listOf(Triple(id, version, group)) },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_DEPENDENCY,
        )

    /**
     * Adds a test dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun testPlugin(notation: Provider<String>) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = notation.map { listOfNotNull(it.parsePluginNotation()) },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_DEPENDENCY,
        )

    /**
     * Adds a test dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun testPlugin(notation: String) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = dependenciesHelper.provider { listOfNotNull(notation.parsePluginNotation()) },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_DEPENDENCY,
        )

    /**
     * Adds test dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun testPlugins(vararg notations: String) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = dependenciesHelper.provider { notations.mapNotNull { it.parsePluginNotation() } },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_DEPENDENCY,
        )

    /**
     * Adds test dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun testPlugins(notations: List<String>) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = dependenciesHelper.provider { notations.mapNotNull { it.parsePluginNotation() } },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_DEPENDENCY,
        )

    /**
     * Adds test dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun testPlugins(notations: Provider<List<String>>) =
        dependenciesHelper.addIntelliJPlatformPluginDependencies(
            pluginsProvider = notations.map { it.mapNotNull { notation -> notation.parsePluginNotation() } },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_DEPENDENCY,
        )

    /**
     * Adds a dependency on a bundled IntelliJ Platform plugin.
     *
     * @param id The bundled plugin identifier.
     */
    fun bundledPlugin(id: String) =
        dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = dependenciesHelper.provider { listOf(id) },
        )

    /**
     * Adds a dependency on a bundled IntelliJ Platform plugin.
     *
     * @param id The provider of the bundled plugin identifier.
     */
    fun bundledPlugin(id: Provider<String>) =
        dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = id.map { listOf(it) },
        )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(vararg ids: String) =
        dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = dependenciesHelper.provider { ids.asList() },
        )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(ids: List<String>) =
        dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = dependenciesHelper.provider { ids },
        )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(ids: Provider<List<String>>) =
        dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = ids,
        )

    /**
     * Adds a test dependency on a bundled IntelliJ Platform plugin.
     *
     * @param id The bundled plugin identifier.
     */
    fun testBundledPlugin(id: String) =
        dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = dependenciesHelper.provider { listOf(id) },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_PLUGINS,
        )

    /**
     * Adds a test dependency on a bundled IntelliJ Platform plugin.
     *
     * @param id The provider of the bundled plugin identifier.
     */
    fun testBundledPlugin(id: Provider<String>) =
        dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = id.map { listOf(it) },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_PLUGINS,
        )

    /**
     * Adds test dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun testBundledPlugins(vararg ids: String) =
        dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = dependenciesHelper.provider { ids.asList() },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_PLUGINS,
        )

    /**
     * Adds test dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun testBundledPlugins(ids: List<String>) =
        dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = dependenciesHelper.provider { ids },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_PLUGINS,
        )

    /**
     * test Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun testBundledPlugins(ids: Provider<List<String>>) =
        dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
            bundledPluginsProvider = ids,
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_PLUGINS,
        )

    /**
     * Adds a dependency on a bundled IntelliJ Platform module.
     *
     * @param id The bundled module identifier.
     */
    fun bundledModule(id: String) =
        dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
            bundledModulesProvider = dependenciesHelper.provider { listOf(id) },
        )

    /**
     * Adds a dependency on a bundled IntelliJ Platform module.
     *
     * @param id The provider of the bundled module identifier.
     */
    fun bundledModule(id: Provider<String>) =
        dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
            bundledModulesProvider = id.map { listOf(it) },
        )

    /**
     * Adds dependencies on bundled IntelliJ Platform modules.
     *
     * @param ids The bundled module identifiers.
     */
    fun bundledModules(vararg ids: String) =
        dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
            bundledModulesProvider = dependenciesHelper.provider { ids.asList() },
        )

    /**
     * Adds dependencies on bundled IntelliJ Platform modules.
     *
     * @param ids The bundled module identifiers.
     */
    fun bundledModules(ids: List<String>) =
        dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
            bundledModulesProvider = dependenciesHelper.provider { ids },
        )

    /**
     * Adds dependencies on bundled IntelliJ Platform modules.
     *
     * @param ids The bundled module identifiers.
     */
    fun bundledModules(ids: Provider<List<String>>) =
        dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
            bundledModulesProvider = ids,
        )

    /**
     * Adds a test dependency on a bundled IntelliJ Platform module.
     *
     * @param id The bundled module identifier.
     */
    fun testBundledModule(id: String) =
        dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
            bundledModulesProvider = dependenciesHelper.provider { listOf(id) },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_MODULES,
        )

    /**
     * Adds a test dependency on a bundled IntelliJ Platform module.
     *
     * @param id The provider of the bundled module identifier.
     */
    fun testBundledModule(id: Provider<String>) =
        dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
            bundledModulesProvider = id.map { listOf(it) },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_MODULES,
        )

    /**
     * Adds test dependencies on bundled IntelliJ Platform modules.
     *
     * @param ids The bundled module identifiers.
     */
    fun testBundledModules(vararg ids: String) =
        dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
            bundledModulesProvider = dependenciesHelper.provider { ids.asList() },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_MODULES,
        )

    /**
     * Adds test dependencies on bundled IntelliJ Platform modules.
     *
     * @param ids The bundled module identifiers.
     */
    fun testBundledModules(ids: List<String>) =
        dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
            bundledModulesProvider = dependenciesHelper.provider { ids },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_MODULES,
        )

    /**
     * Adds test dependencies on bundled IntelliJ Platform modules.
     *
     * @param ids The bundled module identifiers.
     */
    fun testBundledModules(ids: Provider<List<String>>) =
        dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
            bundledModulesProvider = ids,
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_BUNDLED_MODULES,
        )

    /**
     * Adds a dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: File) =
        dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
            localPathProvider = dependenciesHelper.provider { localPath },
        )

    /**
     * Adds a dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: String) =
        dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
            localPathProvider = dependenciesHelper.provider { localPath },
        )

    /**
     * Adds a dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Directory) =
        dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
            localPathProvider = dependenciesHelper.provider { localPath },
        )

    /**
     * Adds a dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Provider<*>) =
        dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
            localPathProvider = localPath,
        )

    /**
     * Adds a dependency on a local IntelliJ Platform plugin.
     *
     * @param dependency Project dependency.
     */
    fun localPlugin(dependency: ProjectDependency) =
        dependenciesHelper.addIntelliJPlatformLocalPluginProjectDependency(
            dependency = dependency,
        )

    /**
     * Adds a test dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun testLocalPlugin(localPath: File) =
        dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
            localPathProvider = dependenciesHelper.provider { localPath },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_LOCAL,
        )

    /**
     * Adds a test dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun testLocalPlugin(localPath: String) =
        dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
            localPathProvider = dependenciesHelper.provider { localPath },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_LOCAL,
        )

    /**
     * Adds a test dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun testLocalPlugin(localPath: Directory) =
        dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
            localPathProvider = dependenciesHelper.provider { localPath },
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_LOCAL,
        )

    /**
     * Adds a test dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun testLocalPlugin(localPath: Provider<*>) =
        dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
            localPathProvider = localPath,
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_LOCAL,
        )

    /**
     * Adds a test dependency on a local IntelliJ Platform plugin.
     *
     * @param dependency Project dependency.
     */
    fun testLocalPlugin(dependency: ProjectDependency) =
        dependenciesHelper.addIntelliJPlatformLocalPluginProjectDependency(
            dependency = dependency,
            configurationName = Configurations.INTELLIJ_PLATFORM_TEST_PLUGIN_LOCAL,
        )

    /**
     * A base method for adding a project dependency on a module to be moved into `lib/modules` by [PrepareSandboxTask].
     *
     * @param dependency Plugin module dependency.
     */
    fun pluginModule(dependency: Dependency?) = dependency?.run {
        dependenciesHelper.addIntelliJPlatformPluginModuleDependency(
            dependency = dependency,
        )
    }

    /**
     * Adds dependency on a module to be merged into the main plugin Jar archive by [ComposedJarTask].
     *
     * @param dependency Plugin composed module dependency.
     */
    fun pluginComposedModule(dependency: Dependency?) = dependency?.run {
        dependenciesHelper.addIntelliJPlatformPluginComposedModuleDependency(
            dependency = dependency,
        )
    }

    /**
     * Adds a dependency on IntelliJ Plugin Verifier.
     *
     * @param version The IntelliJ Plugin Verifier version.
     */
    @JvmOverloads
    fun pluginVerifier(version: String = Constraints.LATEST_VERSION) =
        dependenciesHelper.addPluginVerifierDependency(
            versionProvider = dependenciesHelper.provider { version },
        )

    /**
     * Adds a dependency on IntelliJ Plugin Verifier.
     *
     * @param version The provider of the IntelliJ Plugin Verifier version.
     */
    fun pluginVerifier(version: Provider<String>) =
        dependenciesHelper.addPluginVerifierDependency(
            versionProvider = version,
        )

    /**
     * Adds a dependency on Marketplace ZIP Signer.
     *
     * @param version The Marketplace ZIP Signer version.
     */
    @JvmOverloads
    fun zipSigner(version: String = Constraints.LATEST_VERSION) =
        dependenciesHelper.addZipSignerDependency(
            versionProvider = dependenciesHelper.provider { version },
        )

    /**
     * Adds a dependency on Marketplace ZIP Signer.
     *
     * @param version The provider of the Marketplace ZIP Signer version.
     */
    fun zipSigner(version: Provider<String>) =
        dependenciesHelper.addZipSignerDependency(
            versionProvider = version,
        )

    /**
     * Adds a dependency on the `test-framework` library or its variant, required for testing plugins.
     *
     * There are multiple Test Framework variants available, which provide additional classes for testing specific modules, like:
     * JUnit4, JUnit 5, Maven, JavaScript, Go, Java, ReSharper, etc.
     *
     * The version, if absent, is determined by the IntelliJ Platform build number.
     *
     * @param type Test framework variant type.
     * @param version Test framework library version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @see TestFrameworkType
     */
    @JvmOverloads
    fun testFramework(
        type: TestFrameworkType,
        version: String = Constraints.CLOSEST_VERSION,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES,
    ) = dependenciesHelper.addTestFrameworkDependency(
        type = type,
        versionProvider = dependenciesHelper.provider { version },
        configurationName = configurationName,
    )

    /**
     * Adds a dependency on the `test-framework` library required for testing plugins.
     *
     * There are multiple Test Framework variants available, which provide additional classes for testing specific modules, like:
     * JUnit4, JUnit 5, Maven, JavaScript, Go, Java, ReSharper, etc.
     *
     * The version, if absent, is determined by the IntelliJ Platform build number.
     *
     * @param type Test Framework variant type.
     * @param version Library version.
     * @param configurationName The name of the configuration to add the dependency to.
     * @see TestFrameworkType
     */
    fun testFramework(
        type: TestFrameworkType,
        version: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES,
    ) = dependenciesHelper.addTestFrameworkDependency(
        type = type,
        versionProvider = version,
        configurationName = configurationName,
    )

    /**
     * Adds a dependency on the `test-framework` library or its variant, required for testing plugins.
     *
     * The version, if absent, is determined by the IntelliJ Platform build number.
     *
     * @param coordinates IntelliJ Platform dependency coordinates.
     * @param version IntelliJ Platform dependency version.
     * @param configurationName The name of the configuration to add the dependency to.
     */
    @JvmOverloads
    fun platformDependency(
        coordinates: Coordinates,
        version: String = Constraints.CLOSEST_VERSION,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
    ) = dependenciesHelper.addPlatformDependency(
        coordinates = coordinates,
        versionProvider = dependenciesHelper.provider { version },
        configurationName = configurationName,
    )

    /**
     * Adds a dependency on the `test-framework` library or its variant, required for testing plugins.
     *
     * The version, if absent, is determined by the IntelliJ Platform build number.
     *
     * @param coordinates IntelliJ Platform dependency coordinates.
     * @param version IntelliJ Platform dependency version.
     * @param configurationName The name of the configuration to add the dependency to.
     */
    fun platformDependency(
        coordinates: Coordinates,
        version: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
    ) = dependenciesHelper.addPlatformDependency(
        coordinates = coordinates,
        versionProvider = version,
        configurationName = configurationName,
    )

    /**
     * Adds a dependency on the `test-framework` library or its variant, required for testing plugins.
     *
     * The version, if absent, is determined by the IntelliJ Platform build number.
     *
     * @param coordinates IntelliJ Platform dependency coordinates.
     * @param version IntelliJ Platform dependency version.
     * @param configurationName The name of the configuration to add the dependency to.
     */
    @JvmOverloads
    fun testPlatformDependency(
        coordinates: Coordinates,
        version: String = Constraints.CLOSEST_VERSION,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES,
    ) = dependenciesHelper.addPlatformDependency(
        coordinates = coordinates,
        versionProvider = dependenciesHelper.provider { version },
        configurationName = configurationName,
    )

    /**
     * Adds a dependency on the `test-framework` library or its variant, required for testing plugins.
     *
     * The version, if absent, is determined by the IntelliJ Platform build number.
     *
     * @param coordinates IntelliJ Platform dependency coordinates.
     * @param version IntelliJ Platform dependency version.
     * @param configurationName The name of the configuration to add the dependency to.
     */
    fun testPlatformDependency(
        coordinates: Coordinates,
        version: Provider<String>,
        configurationName: String = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES,
    ) = dependenciesHelper.addPlatformDependency(
        coordinates = coordinates,
        versionProvider = version,
        configurationName = configurationName,
    )

    /**
     * Adds a Java Compiler dependency for code instrumentation.
     *
     * By default, the version is determined by the IntelliJ Platform build number.
     *
     * @param version Java Compiler version.
     */
    @JvmOverloads
    fun javaCompiler(version: String = Constraints.CLOSEST_VERSION) =
        dependenciesHelper.addJavaCompilerDependency(
            versionProvider = dependenciesHelper.provider { version },
        )

    /**
     * Adds a Java Compiler dependency for code instrumentation.
     *
     * By default, the version is determined by the IntelliJ Platform build number.
     *
     * @param version Java Compiler version.
     */
    fun javaCompiler(version: Provider<String>) =
        dependenciesHelper.addJavaCompilerDependency(
            versionProvider = version,
        )

    /**
     * Adds a Grammar Kit dependency to generate lexer code.
     *
     * @param version Grammar Kit version.
     */
    fun grammarKit(version: String = Constraints.GRAMMAR_KIT_VERSION) = dependenciesHelper.addGrammarKitDependency(
        versionProvider = dependenciesHelper.provider { version },
    )

    /**
     * Adds a Grammar Kit dependency to generate lexer code.
     *
     * @param version JFlex version.
     */
    fun grammarKit(version: Provider<String>) = dependenciesHelper.addGrammarKitDependency(
        versionProvider = version,
    )

    /**
     * Adds a JFlex dependency to generate lexer code.
     *
     * @param version JFlex version.
     */
    fun jflex(version: String = Constraints.JFLEX_VERSION) = dependenciesHelper.addJFlexDependency(
        versionProvider = dependenciesHelper.provider { version },
    )

    /**
     * Adds a JFlex dependency to generate lexer code.
     *
     * @param version JFlex version.
     */
    fun jflex(version: Provider<String>) = dependenciesHelper.addJFlexDependency(
        versionProvider = version,
    )

    /**
     * Adds a dependency on a Jar bundled within the current IntelliJ Platform.
     *
     * @param path Path to the bundled Jar file, like `lib/testFramework.jar`.
     */
    fun bundledLibrary(path: String) =
        dependenciesHelper.addBundledLibrary(
            pathProvider = dependenciesHelper.provider { path },
        )

    /**
     * Adds a dependency on a Jar bundled within the current IntelliJ Platform.
     *
     * @param path Path to the bundled Jar file, like `lib/testFramework.jar`.
     */
    fun bundledLibrary(path: Provider<String>) =
        dependenciesHelper.addBundledLibrary(
            pathProvider = path,
        )

    /**
     * Configures and bundles the necessary modules for the Compose UI framework.
     *
     * The method encapsulates and returns a list of essential module identifiers required
     * for integrating and using the Compose UI components in the IntelliJ Platform.
     */
    @Incubating
    fun composeUI() = dependenciesHelper.addComposeUiDependencies()

    companion object {
        fun register(
            dependenciesHelper: IntelliJPlatformDependenciesHelper,
            extensionProvider: Provider<IntelliJPlatformExtension>,
            objects: ObjectFactory,
            target: Any,
        ) =
            target.configureExtension<IntelliJPlatformDependenciesExtension>(
                Extensions.INTELLIJ_PLATFORM,
                dependenciesHelper,
                extensionProvider,
                objects,
            )
    }
}

/**
 * Excludes all Kotlin stdlib transitive dependencies.
 *
 * This helper excludes the following Kotlin stdlib modules:
 * - `kotlin-stdlib`
 * - `kotlin-stdlib-jdk8`
 *
 * Example usage:
 * ```kotlin
 * dependencies {
 *     testImplementation(libs.someLibrary) {
 *         excludeKotlinStdlib()
 *     }
 * }
 * ```
 *
 * @see kotlinStdlib
 */
fun ModuleDependency.excludeKotlinStdlib() {
    kotlinStdlib.forEach { coordinates ->
        exclude(coordinates.groupId, coordinates.artifactId)
    }
}

/**
 * Excludes all Kotlin Coroutines transitive dependencies.
 *
 * This helper excludes the following coroutines modules from both `org.jetbrains.kotlinx` and `com.intellij.platform` groups:
 * - `kotlinx-coroutines-core`
 * - `kotlinx-coroutines-core-jvm`
 * - `kotlinx-coroutines-jdk8`
 * - `kotlinx-coroutines-debug`
 * - `kotlinx-coroutines-guava`
 * - `kotlinx-coroutines-slf4j`
 * - `kotlinx-coroutines-test`
 *
 * Example usage:
 * ```kotlin
 * dependencies {
 *     testImplementation(libs.kotestAssertions) {
 *         excludeCoroutines()
 *     }
 * }
 * ```
 *
 * @see coroutines
 */
fun ModuleDependency.excludeCoroutines() {
    coroutines.forEach { coordinates ->
        exclude(coordinates.groupId, coordinates.artifactId)
    }
}
