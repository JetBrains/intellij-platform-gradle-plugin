// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.DependencyResolutionManagement
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.intellij.platform.gradle.Constants.Extensions
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.CustomPluginRepositoryType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatform
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.plugins.configureExtension
import org.jetbrains.intellij.platform.gradle.utils.rootProjectPath
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.absolute

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
 * @param rootProjectDirectory The root project directory location.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate", "UnstableApiUsage")
@IntelliJPlatform
abstract class IntelliJPlatformRepositoriesExtension @Inject constructor(
    private val repositories: RepositoryHandler,
    providers: ProviderFactory,
    objects: ObjectFactory,
    flowScope: FlowScope,
    flowProviders: FlowProviders,
    gradle: Gradle,
    rootProjectDirectory: Path,
) {

    private val delegate = IntelliJPlatformRepositoriesHelper(
        repositories,
        providers,
        objects,
        flowScope,
        flowProviders,
        gradle,
        rootProjectDirectory,
    )

    /**
     * Adds a repository for accessing IntelliJ Platform stable releases.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun releases(action: MavenRepositoryAction = {}) = delegate.createMavenRepository(
        name = "IntelliJ Repository (Releases)",
        url = "https://www.jetbrains.com/intellij-repository/releases",
        urlWithCacheRedirector = "${Locations.CACHE_REDIRECTOR}/www.jetbrains.com/intellij-repository/releases",
        action = action,
    )

    /**
     * Adds a repository for accessing IntelliJ Platform snapshot releases.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun snapshots(action: MavenRepositoryAction = {}) = delegate.createMavenRepository(
        name = "IntelliJ Repository (Snapshots)",
        url = "https://www.jetbrains.com/intellij-repository/snapshots",
        urlWithCacheRedirector = "${Locations.CACHE_REDIRECTOR}/www.jetbrains.com/intellij-repository/snapshots",
        action = action,
    )

    /**
     * Adds a repository for accessing IntelliJ Platform nightly releases.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun nightly(action: MavenRepositoryAction = {}) = delegate.createMavenRepository(
        name = "IntelliJ Repository (Nightly)",
        url = "https://www.jetbrains.com/intellij-repository/nightly",
        urlWithCacheRedirector = "${Locations.CACHE_REDIRECTOR}/www.jetbrains.com/intellij-repository/nightly",
        action = action,
    )

    /**
     * Adds a repository for accessing IntelliJ Platform dependencies.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun intellijDependencies(action: MavenRepositoryAction = {}) = delegate.createMavenRepository(
        name = "IntelliJ Platform Dependencies Repository",
        url = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies",
        urlWithCacheRedirector = "${Locations.CACHE_REDIRECTOR}/intellij-dependencies",
        action = action,
    )

    /**
     * Adds a repository for accessing plugins hosted on JetBrains Marketplace.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun marketplace(action: MavenRepositoryAction = {}) = delegate.createMavenRepository(
        name = "JetBrains Marketplace Repository",
        url = "https://plugins.jetbrains.com/maven",
        urlWithCacheRedirector = "${Locations.CACHE_REDIRECTOR}/plugins.jetbrains.com/maven",
        action = action,
    )

    fun customPluginRepository(url: String, type: CustomPluginRepositoryType = CustomPluginRepositoryType.PLUGIN_REPOSITORY, action: IvyRepositoryAction = {}) =
        delegate.createCustomPluginRepository(
            repositoryName = "IntelliJ Platform Custom Plugin Repository ($url)",
            repositoryUrl = url,
            repositoryType = type,
            action = action,
        )

    /**
     * Adds a repository for accessing JetBrains Runtime releases.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun jetbrainsRuntime(action: IvyRepositoryAction = {}) = delegate.createIvyRepository(
        name = "JetBrains Runtime",
        url = Locations.JETBRAINS_RUNTIME_REPOSITORY,
        patterns = listOf("[revision].tar.gz"),
        action = action,
    )

    /**
     * Adds a repository for accessing IntelliJ Platform binary releases.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun jetbrainsIdeInstallers(action: IvyRepositoryAction = {}): IvyArtifactRepository = delegate.createIvyRepository(
        name = "JetBrains IDE Installers",
        url = Locations.JETBRAINS_IDES_INSTALLERS,
        patterns = listOf(
            "[organization]/[module]-[revision](-[classifier]).[ext]",
            "[organization]/[module]-[revision](.[classifier]).[ext]",
            "[organization]/[revision]/[module]-[revision](-[classifier]).[ext]",
            "[organization]/[revision]/[module]-[revision](.[classifier]).[ext]",
        ),
        action = {
            repositories.exclusiveContent {
                forRepositories(this@createIvyRepository)
                filter {
                    IntelliJPlatformType.values()
                        .filter { it != IntelliJPlatformType.AndroidStudio }
                        .mapNotNull { it.installer }
                        .forEach {
                            includeModule(it.groupId, it.artifactId)
                        }
                }
            }
            action()
        },
    )

    /**
     * Adds a repository for accessing Android Studio binary releases.
     *
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    fun androidStudioInstallers(action: IvyRepositoryAction = {}) = delegate.createIvyRepository(
        name = "Android Studio Installers",
        url = Locations.ANDROID_STUDIO_INSTALLERS,
        patterns = listOf(
            "/ide-zips/[revision]/[artifact]-[revision]-[classifier].[ext]",
            "/install/[revision]/[artifact]-[revision]-[classifier].[ext]",
        ),
        action = {
            repositories.exclusiveContent {
                forRepositories(this@createIvyRepository)
                filter {
                    val coordinates = IntelliJPlatformType.AndroidStudio.installer
                    requireNotNull(coordinates)

                    includeModule(coordinates.groupId, coordinates.artifactId)
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
    fun localPlatformArtifacts(action: IvyRepositoryAction = {}) = delegate.createLocalIvyRepository(
        repositoryName = "Local IntelliJ Platform Artifacts Repository",
        action = action,
    )

    /**
     * Applies a set of recommended repositories required for running the most common tasks provided by the IntelliJ Platform Gradle Plugin:
     * - [localPlatformArtifacts] — required to use plugins bundled with IntelliJ Platform or refer to the local IDE
     * - [intellijDependencies] — required for resolving extra IntelliJ Platform dependencies used for running specific tasks
     * - [releases] and [snapshots] — IntelliJ Platform releases channels
     * - [marketplace] — JetBrains Marketplace plugins repository
     * - [binaryReleases] — JetBrains IDEs releases required for running the IntelliJ Plugin Verifier
     */
    fun defaultRepositories() {
        jetbrainsIdeInstallers()
        androidStudioInstallers()
        localPlatformArtifacts()
        intellijDependencies()
        releases()
        snapshots()
        marketplace()
    }

    companion object : Registrable<IntelliJPlatformRepositoriesExtension> {
        override fun register(project: Project, target: Any) =
            target.configureExtension<IntelliJPlatformRepositoriesExtension>(
                Extensions.INTELLIJ_PLATFORM,
                project.repositories,
                project.providers,
                project.objects,
                project.serviceOf<FlowScope>(),
                project.serviceOf<FlowProviders>(),
                project.gradle,
                project.rootProjectPath,
            )

        fun register(settings: Settings, target: Any) =
            target.configureExtension<IntelliJPlatformRepositoriesExtension>(
                Extensions.INTELLIJ_PLATFORM,
                settings.dependencyResolutionManagement.repositories,
                settings.providers,
                settings.serviceOf<ObjectFactory>(),
                settings.serviceOf<FlowScope>(),
                settings.serviceOf<FlowProviders>(),
                settings.gradle,
                settings.rootDir.toPath().absolute(),
            )
    }
}

/**
 * A shorthand for accessing the [IntelliJPlatformRepositoriesExtension] in the `settings.gradle.kts` file.
 *
 * ```kotlin
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
internal typealias MavenRepositoryAction = (MavenArtifactRepository.() -> Unit)
internal typealias IvyRepositoryAction = (IvyArtifactRepository.() -> Unit)
