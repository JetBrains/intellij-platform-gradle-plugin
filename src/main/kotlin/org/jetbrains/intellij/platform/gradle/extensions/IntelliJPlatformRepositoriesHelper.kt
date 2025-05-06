// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.artifacts.dsl.RepositoryHandler
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
import org.jetbrains.intellij.platform.gradle.services.registerClassLoaderScopedBuildService
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

    private val shimManager = gradle.registerClassLoaderScopedBuildService(ShimManagerService::class) {
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
            name = repositoryName
            url = shimServer.url
            isAllowInsecureProtocol = true

            patternLayout {
                artifact("[organization]/[module]/[revision]/download")
                ivy("[organization]/[module]/[revision]/descriptor.ivy")
            }

            content {
                includeGroup(Dependencies.MARKETPLACE_GROUP) // TODO: parametrize?
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
            action()
        }
        return repository
    }

    /**
     * @see org.jetbrains.intellij.platform.gradle.models.IvyModule
     */
    internal fun createLocalIvyRepository(repositoryName: String, action: IvyRepositoryAction = {}) = repositories.ivy {
        name = repositoryName

        // Location of Ivy files generated for the current project.
        val localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory)
        ivyPattern("${localPlatformArtifactsPath.pathString}/[revision]/[organization]-[module]-[revision].[ext]")

        // As all artifacts defined in Ivy repositories have a full artifact path set as their names, we can use them to locate artifact files
        artifactPattern("/[artifact]")

        /**
         * Because artifact paths always start with `/`,
         * on Windows, we have to guess to which drive letter the artifact path belongs to.
         * To do so, we add all drive letters (`a:/[artifact]`, `b:/[artifact]`, `c:/[artifact]`, ...) to the stack,
         * starting with `c` for the sake of micro-optimization.
         */
        if (OperatingSystem.current().isWindows) {
            (('c'..'z') + 'a' + 'b').forEach { artifactPattern("$it:/[artifact]") }
        }
    }.apply {
        content {
            includeGroup(Dependencies.BUNDLED_MODULE_GROUP)
            includeGroup(Dependencies.BUNDLED_PLUGIN_GROUP)
            includeGroup(Dependencies.LOCAL_IDE_GROUP)
            includeGroup(Dependencies.LOCAL_PLUGIN_GROUP)
            includeGroup(Dependencies.LOCAL_JETBRAINS_RUNTIME_GROUP)
        }
        action()
    }
}
