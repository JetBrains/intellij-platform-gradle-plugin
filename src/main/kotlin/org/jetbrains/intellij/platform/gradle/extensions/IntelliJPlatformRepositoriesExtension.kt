// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.initialization.resolve.DependencyResolutionManagement
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.maven
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Extensions
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Locations
import org.jetbrains.intellij.platform.gradle.model.toPublication
import org.jetbrains.intellij.platform.gradle.utils.IntelliJPlatformType
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
) {

    /**
     * Adds a repository for accessing IntelliJ Platform stable releases.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun releases(action: RepositoryAction = {}) = createRepository(
        name = "IntelliJ Repository",
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
     * Adds a local Ivy repository for resolving local Ivy XML files used for describing artifacts like local IntelliJ Platform instance, bundled plugins,
     * and other dependencies that utilize [createIvyDependency].
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun ivy(action: RepositoryAction = {}) = repositories.ivy {
        // Location of Ivy files generated for the current project.
        // TODO: make configurable with Gradle properties and align with [createIvyDependency]
        ivyPattern(".gradle/intellijPlatform/ivy/[organization]-[module]-[revision].[ext]")

        // As all artifacts defined in Ivy repositories have a full artifact path set as their names, we can use them to locate artifact files
        artifactPattern("/[artifact]")

        /**
         * Because artifact paths always start with `/` (see [toPublication] for details),
         * on Windows, we have to guess to which drive letter the artifact path belongs to.
         * To do so, we add all drive letters (`a:/[artifact]`, `b:/[artifact]`, `c:/[artifact]`, ...) to the stack,
         * starting with `c` for the sake of micro-optimization.
         */
        if (OperatingSystem.current().isWindows) {
            (('c'..'z') + 'a' + 'b').forEach { artifactPattern("$it:/[artifact]") }
        }
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
     * Applies a set of recommended repositories.
     */
    fun recommended() {
        ivy()
        releases()
        snapshots()
        marketplace()
        jetbrainsRuntime()
        binaryReleases()
        binaryReleasesAndroidStudio()
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
