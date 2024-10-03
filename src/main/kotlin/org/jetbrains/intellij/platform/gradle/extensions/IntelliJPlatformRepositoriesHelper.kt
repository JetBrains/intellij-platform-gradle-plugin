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
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.CustomPluginRepositoryType
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.artifacts.repositories.PluginArtifactRepository
import org.jetbrains.intellij.platform.gradle.flow.StopShimServerAction
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.services.ShimManagerService
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.pathString

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

    internal fun createLocalIvyRepository(repositoryName: String, action: IvyRepositoryAction = {}): IvyArtifactRepository {
        repositories.forEach {
            it.content {
                // For performance reasons exclude the group from already added repos, since we do not expect it to
                // exist in any public repositories.
                // The ones declared after, should not matter, as long as the artifact is found in this repo,
                // because Gradle checks repos in their declaration order.
                // Tests on an env with removed caches show that this is actually necessary to prevent extra requests.
                excludeGroupAndSubgroups(Configurations.Dependencies.JB_LOCAL_PREFIX)
            }
        }

        return repositories.ivy {
            name = repositoryName

            // Location of Ivy files generated for the current project.
            val localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory)
            ivyPattern("${localPlatformArtifactsPath.pathString}/[organization]-[module]-[revision].[ext]")

        // "type" may contain an optional absolute path to the actual location of the artifact, in different situations
        // it may point to a completely unrelated locations on the file system.
        // Yes, attribute named types does not fit very good for storing a path, but there is no better alternative.
        // "artifact" maps to IvyModule#name here we expect to have the second part of the path.
        // "ext" is file extension, e.g. "jar" or "directory", not used here.
        //
        // The type can be empty fur the "artifact" (IvyModule#name) can not.
        //
        // It has to be prefixed by "/" because: If this pattern is not a fully-qualified URL, it will be interpreted
        // as a file relative to the project directory.
        val pattern = "/([type])[artifact]"
        artifactPattern(pattern)

            /**
             * Because artifact paths always start with `/` (see [toPublication] for details),
             * on Windows, we have to guess to which drive letter the artifact path belongs to.
             * To do so, we add all drive letters (`a:/[artifact]`, `b:/[artifact]`, `c:/[artifact]`, ...) to the stack,
             * starting with `c` for the sake of micro-optimization.
             */
            if (OperatingSystem.current().isWindows) {
            (('c'..'z') + 'a' + 'b').forEach { artifactPattern("$it:$pattern") }
            }
        }.apply {
            content {
                includeGroup(Configurations.Dependencies.BUNDLED_MODULE_GROUP)
                includeGroup(Configurations.Dependencies.BUNDLED_PLUGIN_GROUP)
                includeGroup(Configurations.Dependencies.LOCAL_IDE_GROUP)
                includeGroup(Configurations.Dependencies.LOCAL_PLUGIN_GROUP)
                includeGroup(Configurations.Dependencies.LOCAL_JETBRAINS_RUNTIME_GROUP)
            }
            action()
        }
    }
}
