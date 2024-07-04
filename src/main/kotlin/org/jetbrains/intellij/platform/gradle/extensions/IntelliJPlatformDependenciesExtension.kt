// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.Constants.Extensions
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.plugins.configureExtension
import org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask
import org.jetbrains.intellij.platform.gradle.tasks.InstrumentCodeTask
import java.io.File
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.absolute

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
 * @param dependencies The Gradle [DependencyHandler] to manage dependencies.
 * @param providers The Gradle [ProviderFactory] to create providers.
 * @param layout The Gradle [ProjectLayout] to manage layout providers.
 * @param rootProjectDirectory The root project directory location.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@IntelliJPlatform
abstract class IntelliJPlatformDependenciesExtension @Inject constructor(
    configurations: ConfigurationContainer,
    dependencies: DependencyHandler,
    layout: ProjectLayout,
    objects: ObjectFactory,
    providers: ProviderFactory,
    resources: ResourceHandler,
    rootProjectDirectory: Path,
) {

    private val delegate = IntelliJPlatformDependenciesHelper(
        configurations,
        dependencies,
        layout,
        objects,
        providers,
        resources,
        rootProjectDirectory,
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The version of the IntelliJ Platform dependency.
     */
    fun create(type: String, version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { type.toIntelliJPlatformType() },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param version The version of the IntelliJ Platform dependency.
     */
    fun create(type: Provider<*>, version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = type,
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The version of the IntelliJ Platform dependency.
     */
    fun create(type: IntelliJPlatformType, version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { type },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The provider for the version of the IntelliJ Platform dependency.
     */
    fun create(type: String, version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { type.toIntelliJPlatformType() },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The type of the IntelliJ Platform dependency.
     * @param version The provider for the version of the IntelliJ Platform dependency.
     */
    fun create(type: IntelliJPlatformType, version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { type },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param version The provider for the version of the IntelliJ Platform dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     */
    fun create(type: Provider<*>, version: Provider<String>, useInstaller: Boolean = true, configurationName: String) = delegate.addIntelliJPlatformDependency(
        typeProvider = type,
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
        configurationName = configurationName,
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param version The provider for the version of the IntelliJ Platform dependency.
     */
    fun create(type: Provider<*>, version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = type,
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param version The provider for the version of the IntelliJ Platform dependency.
     */
    fun create(type: Provider<*>, version: Provider<String>, useInstaller: Provider<Boolean>) = delegate.addIntelliJPlatformDependency(
        typeProvider = type,
        versionProvider = version,
        useInstallerProvider = useInstaller,
    )

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param notation The IntelliJ Platform dependency. Accepts [String] in `TYPE-VERSION` or `VERSION` format.
     */
    fun create(notation: String, useInstaller: Boolean = true) {
        val (type, version) = notation.parseIdeNotation()

        delegate.addIntelliJPlatformDependency(
            typeProvider = delegate.provider { type },
            versionProvider = delegate.provider { version },
            useInstallerProvider = delegate.provider { useInstaller },
        )
    }

    /**
     * Adds a dependency on the IntelliJ Platform.
     *
     * @param notation The IntelliJ Platform dependency. Accepts [String] in `TYPE-VERSION` or `VERSION` format.
     */
    fun create(notation: Provider<String>, useInstaller: Boolean = true) {
        val parsedNotationProvider = notation.map { it.parseIdeNotation() }

        delegate.addIntelliJPlatformDependency(
            typeProvider = parsedNotationProvider.map { it.first },
            versionProvider = parsedNotationProvider.map { it.second },
            useInstallerProvider = delegate.provider { useInstaller },
        )
    }

    /**
     * Adds a dependency on the custom IntelliJ Platform with a fallback to the base IntelliJ Platform.
     *
     * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param version The provider for the version of the IntelliJ Platform dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     */
    internal fun customCreate(type: Provider<*>, version: Provider<String>, useInstaller: Boolean = true, configurationName: String) =
        delegate.addIntelliJPlatformDependency(
            typeProvider = type,
            versionProvider = version,
            useInstallerProvider = delegate.provider { useInstaller },
            configurationName = configurationName,
            fallbackToBase = true,
        )

    /**
     * Adds a dependency on the custom IntelliJ Platform with a fallback to the base IntelliJ Platform.
     *
     * @param type The provider for the type of the IntelliJ Platform dependency. Accepts either [IntelliJPlatformType] or [String].
     * @param version The provider for the version of the IntelliJ Platform dependency.
     * @param configurationName The name of the configuration to add the dependency to.
     */
    internal fun customCreate(type: Provider<*>, version: Provider<String>, useInstaller: Provider<Boolean>, configurationName: String) =
        delegate.addIntelliJPlatformDependency(
            typeProvider = type,
            versionProvider = version,
            useInstallerProvider = useInstaller,
            configurationName = configurationName,
            fallbackToBase = true,
        )

    /**
     * Adds a dependency on Android Studio.
     *
     * @param version The version of Android Studio.
     */
    fun androidStudio(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.AndroidStudio },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on Android Studio.
     *
     * @param version The provider for the version of Android Studio.
     */
    fun androidStudio(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.AndroidStudio },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on Aqua.
     *
     * @param version The version of Aqua.
     */
    fun aqua(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.Aqua },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on Aqua.
     *
     * @param version The provider for the version of Aqua.
     */
    fun aqua(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.Aqua },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on DataGrip.
     *
     * @param version The version of DataGrip.
     */
    fun datagrip(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.DataGrip },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on DataGrip.
     *
     * @param version The provider for the version of DataGrip.
     */
    fun datagrip(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.DataGrip },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on DataSpell.
     *
     * @param version The version of DataSpell.
     */
    fun dataspell(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.DataSpell },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on DataSpell.
     *
     * @param version The provider for the version of DataSpell.
     */
    fun dataspell(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.DataSpell },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on CLion.
     *
     * @param version The version of CLion.
     */
    fun clion(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.CLion },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on CLion.
     *
     * @param version The provider for the version of CLion.
     */
    fun clion(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.CLion },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on Fleet Backend.
     *
     * @param version The version of Fleet Backend.
     */
    fun fleetBackend(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.FleetBackend },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on Fleet Backend.
     *
     * @param version The provider for the version of Fleet Backend.
     */
    fun fleetBackend(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.FleetBackend },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on Gateway.
     *
     * @param version The version of Gateway.
     */
    fun gateway(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.Gateway },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on Gateway.
     *
     * @param version The provider for the version of Gateway.
     */
    fun gateway(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.Gateway },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on GoLand.
     *
     * @param version The version of GoLand.
     */
    fun goland(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.GoLand },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on GoLand.
     *
     * @param version The provider for the version of GoLand.
     */
    fun goland(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.GoLand },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on IntelliJ IDEA Community.
     *
     * @param version The version of IntelliJ IDEA Community.
     */
    fun intellijIdeaCommunity(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.IntellijIdeaCommunity },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on IntelliJ IDEA Community.
     *
     * @param version The provider for the version of IntelliJ IDEA Community.
     */
    fun intellijIdeaCommunity(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.IntellijIdeaCommunity },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on IntelliJ IDEA Ultimate.
     *
     * @param version The version of IntelliJ IDEA Ultimate.
     */
    fun intellijIdeaUltimate(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.IntellijIdeaUltimate },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on IntelliJ IDEA Ultimate.
     *
     * @param version The provider for the version of IntelliJ IDEA Ultimate.
     */
    fun intellijIdeaUltimate(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.IntellijIdeaUltimate },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on MPS.
     *
     * @param version The version of MPS.
     */
    fun mps(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.MPS },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on MPS.
     *
     * @param version The provider for the version of MPS.
     */
    fun mps(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.MPS },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on PhpStorm.
     *
     * @param version The version of PhpStorm.
     */
    fun phpstorm(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.PhpStorm },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on PhpStorm.
     *
     * @param version The provider for the version of PhpStorm.
     */
    fun phpstorm(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.PhpStorm },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on PyCharm Community.
     *
     * @param version The version of PyCharm Community.
     */
    fun pycharmCommunity(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.PyCharmCommunity },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on PyCharm Community.
     *
     * @param version The provider for the version of PyCharm Community.
     */
    fun pycharmCommunity(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.PyCharmCommunity },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on PyCharm Professional.
     *
     * @param version The version of PyCharm Professional.
     */
    fun pycharmProfessional(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.PyCharmProfessional },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on PyCharm Professional.
     *
     * @param version The provider for the version of PyCharm Professional.
     */
    fun pycharmProfessional(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.PyCharmProfessional },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on Rider.
     *
     * @param version The version of Rider.
     */
    fun rider(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.Rider },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on Rider.
     *
     * @param version The provider for the version of Rider.
     */
    fun rider(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.Rider },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on RubyMine.
     *
     * @param version The version of RubyMine.
     */
    fun rubymine(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.RubyMine },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on RubyMine.
     *
     * @param version The provider for the version of RubyMine.
     */
    fun rubymine(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.RubyMine },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on Rust Rover.
     *
     * @param version The version of Rust Rover.
     */
    fun rustRover(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.RustRover },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on Rust Rover.
     *
     * @param version The provider for the version of Rust Rover.
     */
    fun rustRover(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.RustRover },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on WebStorm.
     *
     * @param version The version of WebStorm.
     */
    fun webstorm(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.WebStorm },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on WebStorm.
     *
     * @param version The provider for the version of WebStorm.
     */
    fun webstorm(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.WebStorm },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on Writerside.
     *
     * @param version The version of Writerside.
     */
    fun writerside(version: String, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.Writerside },
        versionProvider = delegate.provider { version },
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a dependency on Writerside.
     *
     * @param version The provider for the version of Writerside.
     */
    fun writerside(version: Provider<String>, useInstaller: Boolean = true) = delegate.addIntelliJPlatformDependency(
        typeProvider = delegate.provider { IntelliJPlatformType.Writerside },
        versionProvider = version,
        useInstallerProvider = delegate.provider { useInstaller },
    )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The local path of the IntelliJ Platform dependency
     */
    fun local(localPath: String) = delegate.addIntelliJPlatformLocalDependency(
        localPathProvider = delegate.provider { localPath },
    )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The local path of the IntelliJ Platform dependency
     */
    fun local(localPath: File) = delegate.addIntelliJPlatformLocalDependency(
        localPathProvider = delegate.provider { localPath },
    )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The local path of the IntelliJ Platform dependency
     */
    fun local(localPath: Directory) = delegate.addIntelliJPlatformLocalDependency(
        localPathProvider = delegate.provider { localPath },
    )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     */
    fun local(localPath: Provider<*>) = delegate.addIntelliJPlatformLocalDependency(
        localPathProvider = localPath,
    )

    /**
     * Adds a local dependency on a local IntelliJ Platform instance.
     *
     * @param localPath The provider for the local path of the IntelliJ Platform dependency. Accepts either [String], [File], or [Directory].
     * @param configurationName The name of the configuration to add the dependency to.
     */
    fun local(localPath: Provider<*>, configurationName: String) = delegate.addIntelliJPlatformLocalDependency(
        localPathProvider = localPath,
        configurationName = configurationName,
    )

    /**
     * Adds a dependency on JetBrains Runtime.
     * The version is calculated using the current IntelliJ Platform.
     */
    fun jetbrainsRuntime() = delegate.addJetBrainsRuntimeObtainedDependency()

    /**
     * Adds a dependency on JetBrains Runtime.
     *
     * @param explicitVersion The explicit version of the JetBrains Runtime.
     */
    fun jetbrainsRuntimeExplicit(explicitVersion: String) = delegate.addJetBrainsRuntimeDependency(
        explicitVersionProvider = delegate.provider { explicitVersion },
    )

    /**
     * Adds a dependency on JetBrains Runtime.
     *
     * @param explicitVersion The provider for the explicit version of the JetBrains Runtime.
     */
    fun jetbrainsRuntimeExplicit(explicitVersion: Provider<String>) = delegate.addJetBrainsRuntimeDependency(
        explicitVersionProvider = explicitVersion,
    )

    /**
     * Adds a local dependency on JetBrains Runtime.
     *
     * @param version The JetBrains Runtime version.
     * @param variant The JetBrains Runtime variant.
     * @param architecture The JetBrains Runtime architecture.
     */
    fun jetbrainsRuntime(version: String, variant: String? = null, architecture: String? = null) = delegate.addJetBrainsRuntimeDependency(
        explicitVersionProvider = delegate.provider {
            delegate.buildJetBrainsRuntimeVersion(version, variant, architecture)
        },
    )

    /**
     * Adds a local dependency on JetBrains Runtime.
     *
     * @param version The provider of the JetBrains Runtime version.
     * @param variant The provider of the JetBrains Runtime variant.
     * @param architecture The provider of the JetBrains Runtime architecture.
     */
    fun jetbrainsRuntime(version: Provider<String>, variant: Provider<String>, architecture: Provider<String>) = delegate.addJetBrainsRuntimeDependency(
        explicitVersionProvider = delegate.provider {
            delegate.buildJetBrainsRuntimeVersion(version.get(), variant.orNull, architecture.orNull)
        },
    )

    /**
     * Adds a dependency on JetBrains Runtime local instance.
     *
     * @param localPath Path to the local JetBrains Runtime.
     */
    fun jetbrainsRuntimeLocal(localPath: String) = delegate.addJetBrainsRuntimeLocalDependency(
        localPathProvider = delegate.provider { localPath },
    )

    /**
     * Adds a dependency on JetBrains Runtime local instance.
     *
     * @param localPath Path to the local JetBrains Runtime.
     */
    fun jetbrainsRuntimeLocal(localPath: Directory) = delegate.addJetBrainsRuntimeLocalDependency(
        localPathProvider = delegate.provider { localPath },
    )

    /**
     * Adds a dependency on JetBrains Runtime local instance.
     *
     * @param localPath Path to the local JetBrains Runtime.
     */
    fun jetbrainsRuntimeLocal(localPath: Path) = delegate.addJetBrainsRuntimeLocalDependency(
        localPathProvider = delegate.provider { localPath },
    )

    /**
     * Adds a dependency on JetBrains Runtime local instance.
     *
     * @param localPath Path to the local JetBrains Runtime.
     */
    fun jetbrainsRuntimeLocal(localPath: Provider<*>) = delegate.addJetBrainsRuntimeLocalDependency(
        localPathProvider = localPath,
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The plugin identifier.
     * @param version The plugin version.
     * @param channel The plugin distribution channel.
     */
    fun plugin(id: String, version: String, channel: String = "") = delegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = delegate.provider { listOf(Triple(id, version, channel)) },
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The provider of the plugin identifier.
     * @param version The provider of the plugin version.
     * @param channel The provider of the plugin distribution channel.
     */
    fun plugin(id: Provider<String>, version: Provider<String>, channel: Provider<String>) = delegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = delegate.provider { listOf(Triple(id.get(), version.get(), channel.get())) },
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: Provider<String>) = delegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = notation.map { listOfNotNull(it.parsePluginNotation()) },
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: String) = delegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = delegate.provider { listOfNotNull(notation.parsePluginNotation()) },
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(vararg notations: String) = delegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = delegate.provider { notations.mapNotNull { it.parsePluginNotation() } },
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: List<String>) = delegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = delegate.provider { notations.mapNotNull { it.parsePluginNotation() } },
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: Provider<List<String>>) = delegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = notations.map { it.mapNotNull { notation -> notation.parsePluginNotation() } },
    )

    /**
     * Adds a dependency on a bundled IntelliJ Platform plugin.
     *
     * @param id The bundled plugin identifier.
     */
    fun bundledPlugin(id: String) = delegate.addIntelliJPlatformBundledPluginDependencies(
        bundledPluginsProvider = delegate.provider { listOf(id) },
    )

    /**
     * Adds a dependency on a bundled IntelliJ Platform plugin.
     *
     * @param id The provider of the bundled plugin identifier.
     */
    fun bundledPlugin(id: Provider<String>) = delegate.addIntelliJPlatformBundledPluginDependencies(
        bundledPluginsProvider = id.map { listOf(it) },
    )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(vararg ids: String) = delegate.addIntelliJPlatformBundledPluginDependencies(
        bundledPluginsProvider = delegate.provider { ids.asList() },
    )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(ids: List<String>) = delegate.addIntelliJPlatformBundledPluginDependencies(
        bundledPluginsProvider = delegate.provider { ids },
    )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(ids: Provider<List<String>>) = delegate.addIntelliJPlatformBundledPluginDependencies(
        bundledPluginsProvider = ids,
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: File) = delegate.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = delegate.provider { localPath },
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: String) = delegate.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = delegate.provider { localPath },
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Directory) = delegate.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = delegate.provider { localPath },
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Provider<*>) = delegate.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = localPath,
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param dependency Project dependency.
     */
    fun localPlugin(dependency: ProjectDependency) = delegate.addIntelliJPlatformLocalPluginProjectDependency(
        dependency = dependency,
    )

    /**
     * Adds dependency on a module to be merged into the main plugin Jar archive by [ComposedJarTask].
     *
     * @param dependency Plugin module dependency.
     */
    fun pluginModule(dependency: Dependency?) = dependency?.run {
        delegate.addIntelliJPlatformPluginModuleDependency(
            dependency = dependency,
        )
    }

    /**
     * Adds a dependency on IntelliJ Plugin Verifier.
     *
     * @param version The IntelliJ Plugin Verifier version.
     */
    fun pluginVerifier(version: String = Constraints.LATEST_VERSION) = delegate.addPluginVerifierDependency(
        versionProvider = delegate.provider { version },
    )

    /**
     * Adds a dependency on IntelliJ Plugin Verifier.
     *
     * @param version The provider of the IntelliJ Plugin Verifier version.
     */
    fun pluginVerifier(version: Provider<String>) = delegate.addPluginVerifierDependency(
        versionProvider = version,
    )

    /**
     * Adds a dependency on Marketplace ZIP Signer.
     *
     * @param version The Marketplace ZIP Signer version.
     */
    fun zipSigner(version: String = Constraints.LATEST_VERSION) = delegate.addZipSignerDependency(
        versionProvider = delegate.provider { version },
    )

    /**
     * Adds a dependency on Marketplace ZIP Signer.
     *
     * @param version The provider of the Marketplace ZIP Signer version.
     */
    fun zipSigner(version: Provider<String>) = delegate.addZipSignerDependency(
        versionProvider = version,
    )

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
    fun testFramework(type: TestFrameworkType, version: String = Constraints.CLOSEST_VERSION) = delegate.addTestFrameworkDependency(
        type = type,
        versionProvider = delegate.provider { version },
    )

    /**
     * Adds a dependency on the `test-framework` library required for testing plugins.
     *
     * There are multiple Test Framework variants available, which provide additional classes for testing specific modules, like:
     * JUnit4, JUnit 5, Maven, JavaScript, Go, Java, ReSharper, etc.
     *
     * The version, if absent, is determined by the IntelliJ Platform build number.
     * If the exact version is unavailable, and the [BuildFeature.USE_CLOSEST_VERSION_RESOLVING] flag is set to `true`,
     * the closest available version is used, found by scanning all releases in the repository.
     *
     * @param type test framework variant type
     * @param version library version
     * @see TestFrameworkType
     */
    fun testFramework(type: TestFrameworkType, version: Provider<String>) = delegate.addTestFrameworkDependency(
        type = type,
        versionProvider = version,
    )

    /**
     * Adds a dependency on the `test-framework` library or its variant, required for testing plugins.
     *
     * The version, if absent, is determined by the IntelliJ Platform build number.
     * If the exact version is unavailable, and the [BuildFeature.USE_CLOSEST_VERSION_RESOLVING] flag is set to `true`,
     * the closest available version is used, found by scanning all releases in the repository.
     *
     * @param coordinates IntelliJ Platform dependency coordinates
     * @param artifactId IntelliJ Platform dependency artifactId
     * @param version IntelliJ Platform dependency version
     */
    fun platformDependency(coordinates: Coordinates, version: String = Constraints.CLOSEST_VERSION) = delegate.addPlatformDependency(
        coordinates = coordinates,
        versionProvider = delegate.provider { version },
        configurationName = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
    )

    /**
     * Adds a dependency on the `test-framework` library or its variant, required for testing plugins.
     *
     * The version, if absent, is determined by the IntelliJ Platform build number.
     * If the exact version is unavailable, and the [BuildFeature.USE_CLOSEST_VERSION_RESOLVING] flag is set to `true`,
     * the closest available version is used, found by scanning all releases in the repository.
     *
     * @param coordinates IntelliJ Platform dependency coordinates
     * @param artifactId IntelliJ Platform dependency artifactId
     * @param version IntelliJ Platform dependency version
     */
    fun platformDependency(coordinates: Coordinates, version: Provider<String>) = delegate.addPlatformDependency(
        coordinates = coordinates,
        versionProvider = version,
        configurationName = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
    )

    /**
     * Adds a dependency on the `test-framework` library or its variant, required for testing plugins.
     *
     * The version, if absent, is determined by the IntelliJ Platform build number.
     * If the exact version is unavailable, and the [BuildFeature.USE_CLOSEST_VERSION_RESOLVING] flag is set to `true`,
     * the closest available version is used, found by scanning all releases in the repository.
     *
     * @param groupId IntelliJ Platform dependency groupId
     * @param artifactId IntelliJ Platform dependency artifactId
     * @param version IntelliJ Platform dependency version
     */
    fun testPlatformDependency(coordinates: Coordinates, version: String = Constraints.CLOSEST_VERSION) = delegate.addPlatformDependency(
        coordinates = coordinates,
        versionProvider = delegate.provider { version },
        configurationName = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES,
    )

    /**
     * Adds a dependency on the `test-framework` library or its variant, required for testing plugins.
     *
     * The version, if absent, is determined by the IntelliJ Platform build number.
     * If the exact version is unavailable, and the [BuildFeature.USE_CLOSEST_VERSION_RESOLVING] flag is set to `true`,
     * the closest available version is used, found by scanning all releases in the repository.
     *
     * @param groupId IntelliJ Platform dependency groupId
     * @param artifactId IntelliJ Platform dependency artifactId
     * @param version IntelliJ Platform dependency version
     */
    fun testPlatformDependency(coordinates: Coordinates, version: Provider<String>) = delegate.addPlatformDependency(
        coordinates = coordinates,
        versionProvider = version,
        configurationName = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES,
    )

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
    fun javaCompiler(version: String = Constraints.CLOSEST_VERSION) = delegate.addJavaCompilerDependency(
        versionProvider = delegate.provider { version },
    )

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
    fun javaCompiler(version: Provider<String>) = delegate.addJavaCompilerDependency(
        versionProvider = version,
    )

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
    fun bundledLibrary(path: String) = delegate.addBundledLibrary(
        pathProvider = delegate.provider { path },
    )

    /**
     * Adds a dependency on a Jar bundled within the current IntelliJ Platform.
     *
     * @param path Path to the bundled Jar file, like `lib/testFramework.jar`
     */
    fun bundledLibrary(path: Provider<String>) = delegate.addBundledLibrary(
        pathProvider = path,
    )

    companion object : Registrable<IntelliJPlatformDependenciesExtension> {
        override fun register(project: Project, target: Any) =
            target.configureExtension<IntelliJPlatformDependenciesExtension>(
                Extensions.INTELLIJ_PLATFORM,
                project.configurations,
                project.dependencies,
                project.layout,
                project.objects,
                project.providers,
                project.resources,
                project.rootProject.rootDir.toPath().absolute(),
            )
    }
}
