// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Dependencies
import org.jetbrains.intellij.platform.gradle.CustomPluginRepositoryType
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.artifacts.repositories.PluginArtifactRepository
import org.jetbrains.intellij.platform.gradle.flow.StopShimServerAction
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.services.ShimManagerService
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.invariantSeparatorsPathString

private const val SHIM_MANAGER = "shimManager"

@Suppress("UnstableApiUsage")
class IntelliJPlatformRepositoriesHelper(
    private val repositories: RepositoryHandler,
    private val providers: ProviderFactory,
    private val objects: ObjectFactory,
    private val flowScope: FlowScope,
    private val flowProviders: FlowProviders,
    private val gradle: Gradle,
    private val rootProjectDirectory: Path,
) {

    private val shimManager = gradle.sharedServices.registerIfAbsent(SHIM_MANAGER, ShimManagerService::class) {
        parameters.port = providers[GradleProperties.ShimServerPort]
    }

    /**
     * Creates a Maven repository for accessing dependencies.
     *
     * @param name The name of the repository.
     * @param url The URL of the repository.
     * @param urlWithCacheRedirector The URL of the repository with the cache redirector. Used when [GradleProperties.UseCacheRedirector] is enabled.
     * @param action The action to be performed on the repository. Defaults to an empty action.
     * @see GradleProperties.UseCacheRedirector
     */
    internal fun createMavenRepository(
        name: String,
        url: String,
        urlWithCacheRedirector: String = url,
        action: MavenRepositoryAction = {},
    ) = providers[GradleProperties.UseCacheRedirector]
        .map { useCacheRedirector ->
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
        .get()

    /**
     * Creates an Ivy repository for accessing dependencies.
     *
     * @param name The name of the repository.
     * @param url The URL of the repository.
     * @param patterns The patterns used for artifact resolution. Defaults to an empty list.
     * @param action The action to be performed on the repository. Defaults to an empty action.
     */
    internal fun createIvyRepository(
        name: String,
        url: String,
        patterns: List<String> = emptyList(),
        action: IvyRepositoryAction = {},
    ) = repositories.ivy {
        this.name = name
        this.url = URI(url)
        patternLayout { patterns.forEach { artifact(it) } }
        metadataSources { artifact() }
        action()
    }

    internal fun createCustomPluginRepository(
        repositoryName: String,
        repositoryUrl: String,
        repositoryType: CustomPluginRepositoryType,
        action: IvyRepositoryAction = {},
    ): PluginArtifactRepository {
        val repository = objects.newInstance<PluginArtifactRepository>(repositoryName, URI(repositoryUrl), repositoryType, true)
        val shimServer = shimManager.get().start(repository)

        flowScope.always(StopShimServerAction::class) {
            parameters.url = repository.url
            parameters.buildResult = flowProviders.buildWorkResult.map { !it.failure.isPresent }
        }

        repositories.ivy {
            url = shimServer.url
            isAllowInsecureProtocol = true

            patternLayout {
                artifact("[module]/[revision]/download")
                ivy("[module]/[revision]/descriptor.ivy")
            }

            metadataSources {
                ivyDescriptor()
                ignoreGradleMetadataRedirection()
            }

            repository.runCatching {
                getCredentials(PasswordCredentials::class.java).let {
                    credentials(PasswordCredentials::class) {
                        username = it.username
                        password = it.password
                    }
                }
            }
            repository.runCatching {
                getCredentials(HttpHeaderCredentials::class.java).let {
                    credentials(HttpHeaderCredentials::class) {
                        name = it.name
                        value = it.value
                    }
                    authentication {
                        create<HttpHeaderAuthentication>("header")
                    }
                }
            }
        }.apply(action)

        return repository
    }

    /**
     * @see org.jetbrains.intellij.platform.gradle.models.IvyModule
     */
    internal fun createLocalIvyRepository(repositoryName: String, action: IvyRepositoryAction = {}): IvyArtifactRepository {
        // The contract is that we are working with absolute normalized paths here.
        val ivyLocationPath = providers.localPlatformArtifactsPath(rootProjectDirectory).absolute().normalize()

        return createExclusiveIvyRepository(
            repositoryName,
            setOf(
                Dependencies.LOCAL_IDE_GROUP,
                Dependencies.LOCAL_PLUGIN_GROUP,
                Dependencies.LOCAL_JETBRAINS_RUNTIME_GROUP,
                Dependencies.BUNDLED_MODULE_GROUP,
                Dependencies.BUNDLED_PLUGIN_GROUP
            ),
            ivyLocationPath,
            action
        )
    }

    private fun createExclusiveIvyRepository(
        repositoryName: String,
        exclusiveGroups: Set<String> = emptySet(),
        ivyLocationPath: Path,
        action: IvyRepositoryAction = {}
    ): IvyArtifactRepository {
        val repository = createIvyArtifactRepository(
            repositoryName, ivyLocationPath
        )

        // For performance and security reasons, make it exclusive.
        // https://docs.gradle.org/current/userguide/declaring_repositories_adv.html#declaring_content_exclusively_found_in_one_repository
        repositories.exclusiveContent {
            forRepository {
                repository
            }

            filter {
                for (exclusiveGroup in exclusiveGroups) {
                    includeGroup(exclusiveGroup)
                    // Could be improved some day to the next, but today it fails on Gradle 8.2, works on 8.10.2
                    //includeGroupAndSubgroups(exclusiveGroup)
                }
            }
        }

        repository.apply {
            action()
        }

        return repository
    }

    /**
     * @see org.jetbrains.intellij.platform.gradle.models.IvyModule
     * @see org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesHelper.registerIntellijPlatformIvyRepo
     */
    private fun createIvyArtifactRepository(
        repositoryName: String,
        ivyLocationPath: Path
    ) = repositories.ivy {
        name = repositoryName

        // The contract is that we are working with absolute normalized paths here.
        val absNormIvyLocationPath = ivyLocationPath.absolute().normalize()

        // Location of Ivy files generated for the current project.
        val ivyPath = absNormIvyLocationPath
            .absolute()
            .normalize()
            .invariantSeparatorsPathString
            .removeSuffix("/")

        ivyPattern("$ivyPath/[organization]-[module]-[revision].[ext]")

        // In different situations, it may point to a completely unrelated locations on the file system.

        // It has to be prefixed by "/" because if this pattern is not a fully qualified URL, it will be interpreted
        // as a file relative to the project directory, even if the "artifact" (IvyModule#name) starts with a "/".
        // We cannot rid of this leading separator as long as the repository created by createLocalIvyRepository
        // or (localPlatformArtifacts() in public API) is used to reference completely unrelated paths. If they were
        // split into multiple repositories, each could be prefixed with an absolute path specific to that repo.
        //
        // See org.gradle.api.internal.artifacts.repositories.PatternHelper.substituteTokens
        val artifactPattern = "/[artifact]"
        artifactPattern(artifactPattern)

        /**
         * Because artifact paths always start with `/` (see [toPublication] for details),
         * on Windows, we have to guess to which drive letter the artifact path belongs to.
         * To do so, we add all drive letters (`a:/[artifact]`, `b:/[artifact]`, `c:/[artifact]`, ...) to the stack,
         * starting with `c` for the sake of micro-optimization.
         */
        if (OperatingSystem.current().isWindows) {
            (('c'..'z') + 'a' + 'b').forEach { artifactPattern("$it:$artifactPattern") }
        }
    }
}
