// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.maven
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Extensions
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import java.io.File
import java.net.URI
import javax.inject.Inject

/**
 * This is an extension class for managing IntelliJ Platform repositories in a Gradle build script. It's applied to the [RepositoryHandler].
 * Available in both [Project] scope and Gradle Settings for [DependencyResolutionManagement].
 *
 * It provides methods to add:
 *
 *  - IntelliJ Platform repositories (for releases, snapshots, and nightly builds)
 *  - JetBrains Marketplace repository for fetching plugins
 *  - JetBrains Runtime repository
 *  - Android Studio and IntelliJ Platform binary release repositories (for IntelliJ Plugin Verifier)
 *  - Ivy local repository (for correct access to local dependencies)
 *
 * @param repositories The Gradle [RepositoryHandler] to manage repositories.
 * @param providers The Gradle [ProviderFactory] to create providers.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@IntelliJPlatform
abstract class IntelliJPlatformRepositoriesExtension @Inject constructor(
    private val repositories: RepositoryHandler,
    private val providers: ProviderFactory,
    private val localPlatformArtifactsDirectory: File,
) {

    /**
     * Adds a repository for accessing IntelliJ Platform stable releases.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun releases(action: RepositoryAction = {}) = createRepository(
        name = "IntelliJ Repository (Releases)",
        url = "https://www.jetbrains.com/intellij-repository/releases",
        urlWithCacheRedirector = "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases",
        action = action,
    )

    /**
     * Adds a repository for accessing IntelliJ Platform snapshot releases.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun snapshots(action: RepositoryAction = {}) = createRepository(
        name = "IntelliJ Repository (Snapshots)",
        url = "https://www.jetbrains.com/intellij-repository/snapshots",
        urlWithCacheRedirector = "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/snapshots",
        action = action,
    )

    /**
     * Adds a repository for accessing IntelliJ Platform nightly releases.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun nightly(action: RepositoryAction = {}) = createRepository(
        name = "IntelliJ Repository (Nightly)",
        url = "https://www.jetbrains.com/intellij-repository/nightly",
        urlWithCacheRedirector = "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/nightly",
        action = action,
    )

    /**
     * Adds a repository for accessing IntelliJ Platform dependencies.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun intellijDependencies(action: RepositoryAction = {}) = createRepository(
        name = "IntelliJ Platform Dependencies Repository",
        url = "https://cache-redirector.jetbrains.com/intellij-dependencies",
        urlWithCacheRedirector = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies",
        action = action,
    )

    /**
     * Adds a repository for accessing plugins hosted on JetBrains Marketplace.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun marketplace(action: RepositoryAction = {}) = createRepository(
        name = "JetBrains Marketplace Repository",
        url = "https://plugins.jetbrains.com/maven",
        urlWithCacheRedirector = "https://cache-redirector.jetbrains.com/plugins.jetbrains.com/maven",
        action = action,
    )

    /**
     * Adds a repository for accessing JetBrains Runtime releases.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun jetbrainsRuntime(action: RepositoryAction = {}) = createIvyRepository(
        name = "JetBrains Runtime",
        url = Locations.JETBRAINS_RUNTIME_REPOSITORY,
        pattern = "[revision].tar.gz",
        action = action,
    )

    /**
     * Adds a repository for accessing Android Studio binary releases.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun binaryReleasesAndroidStudio(action: RepositoryAction = {}) = createIvyRepository(
        name = "Android Studio Binary Releases",
        url = Locations.ANDROID_STUDIO_BINARY_RELEASES,
        pattern = "/[revision]/android-studio-[revision]-linux.tar.gz",
        action = {
            repositories.exclusiveContent {
                forRepositories(this@createIvyRepository)
                filter {
                    IntelliJPlatformType.AndroidStudio.binary?.let {
                        includeModule(it.group, it.name)
                    }
                }
            }
            action()
        },
    )

    /**
     * Adds a repository for accessing IntelliJ Platform binary releases.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun binaryReleases(action: RepositoryAction = {}) = createIvyRepository(
        name = "IntelliJ IDEA Binary Releases",
        url = Locations.DOWNLOAD,
        pattern = "[organization]/[module]-[revision].[ext]",
        action = {
            repositories.exclusiveContent {
                forRepositories(this@createIvyRepository)
                filter {
                    IntelliJPlatformType.values()
                        .filter { it != IntelliJPlatformType.AndroidStudio }
                        .mapNotNull { it.binary }
                        .forEach {
                            includeModule(it.group, it.name)
                        }
                }
            }
            action()
        },
    )

    // TODO: check the case when marketplace() is higher on the list — most likely it takes the precedence over ivy and fails on built-in java plugin
    //       see https://stackoverflow.com/questions/23023069/gradle-download-and-unzip-file-from-url/34327202#34327202 and exclusiveContent
    // TODO: check if the bundled plugin hash matters — if it has to be different every time as always extract transformer is called, so previous dir may no longer exist

    /**
     * Certain dependencies, such as the IntelliJ Platform and bundled IDE plugins, need extra pre-processing before
     * they can be correctly used by the IntelliJ Platform Gradle Plugin and loaded by Gradle.
     *
     * This pre-processing involves generating XML files that detail these specific artifacts.
     * Once created, these XMLs are stored in a unique custom Ivy repository directory.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun localPlatformArtifacts(action: RepositoryAction = {}) = repositories.ivy {
        // Location of Ivy files generated for the current project.
        setUrl(localPlatformArtifactsDirectory.toPath().toUri())
        ivyPattern("/[organization]-[module]-[revision].[ext]")

        // As all artifacts defined in Ivy repositories have a full artifact path set as their names, we can use them to locate artifact files
        artifactPattern("/[artifact]")
    }.apply {
        repositories.exclusiveContent {
            forRepositories(this@apply)
            filter {
                includeGroup(Configurations.Dependencies.BUNDLED_PLUGIN_GROUP)
                includeGroup(Configurations.Dependencies.LOCAL_IDE_GROUP)
            }
        }
        action()
    }

    /**
     * Applies a set of recommended repositories required for running the most common tasks provided by the IntelliJ Platform Gradle Plugin:
     * - [localPlatformArtifacts] — required to use plugins bundled with IntelliJ Platform or refer to the local IDE
     * - [intellijDependencies] — required for resolving extra IntelliJ Platform dependencies used for running specific tasks
     * - [releases] and [snapshots] — IntelliJ Platform releases channels
     * - [marketplace] — JetBrains Marketplace plugins repository
     * - [binaryReleases] — JetBrains IDEs releases required for running the IntelliJ Plugin Verifier
     */
    fun defaultRepositories() {
        localPlatformArtifacts()
        intellijDependencies()
        releases()
        snapshots()
        marketplace()
        binaryReleases()
    }

    /**
     * Creates a Maven repository for accessing dependencies.
     *
     * @param name The name of the repository.
     * @param url The URL of the repository.
     * @param urlWithCacheRedirector The URL of the repository with cache redirector. Used when [BuildFeature.USE_CACHE_REDIRECTOR] is enabled.
     * @param action The action to be performed on the repository. Defaults to an empty action.
     * @see BuildFeature.USE_CACHE_REDIRECTOR
     */
    private fun createRepository(
        name: String,
        url: String,
        urlWithCacheRedirector: String = url,
        action: RepositoryAction = {},
    ) = BuildFeature.USE_CACHE_REDIRECTOR.getValue(providers).get().let { useCacheRedirector ->
        repositories.maven(
            when (useCacheRedirector) {
                true -> urlWithCacheRedirector
                false -> url
            }
        ) {
            this.name = name
            action()
        }
    }

    /**
     * Creates an Ivy repository for accessing dependencies.
     *
     * @param name The name of the repository.
     * @param url The URL of the repository.
     * @param pattern The pattern used for artifact resolution. Defaults to an empty string.
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    private fun createIvyRepository(
        name: String,
        url: String,
        pattern: String = "",
        action: RepositoryAction = {},
    ) = repositories.ivy {
        this.name = name
        this.url = URI(url)
        patternLayout { artifact(pattern) }
        metadataSources { artifact() }
        action()
    }
}

/**
 * A shorthand for accessing the [IntelliJPlatformRepositoriesExtension] in the `settings.gradle.kts` file.
 *
 * ```
 * import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform
 *
 * dependencyResolutionManagement {
 *     repositories {
 *         intellijPlatform { ... }
 *     }
 * }
 * ```
 */
fun RepositoryHandler.intellijPlatform(configure: Action<IntelliJPlatformRepositoriesExtension>) =
    (this as ExtensionAware).extensions.configure(Extensions.INTELLIJ_PLATFORM, configure)

/**
 * Type alias for a lambda function that takes a [ArtifactRepository] and performs some actions on it.
 */
internal typealias RepositoryAction = (ArtifactRepository.() -> Unit)
