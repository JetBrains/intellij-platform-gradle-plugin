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
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Services
import org.jetbrains.intellij.platform.gradle.CustomPluginRepositoryType
import org.jetbrains.intellij.platform.gradle.artifacts.repositories.PluginArtifactRepository
import org.jetbrains.intellij.platform.gradle.flow.StopShimServerAction
import org.jetbrains.intellij.platform.gradle.services.ShimManagerService
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.pathString

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

    private val shimManager = gradle.sharedServices.registerIfAbsent(Services.SHIM_MANAGER, ShimManagerService::class) {
        parameters.port = 7348 // TODO: read from Gradle properties
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
    internal fun createMavenRepository(
        name: String,
        url: String,
        urlWithCacheRedirector: String = url,
        action: MavenRepositoryAction = {},
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
        val repository = objects.newInstance<PluginArtifactRepository>(repositoryName, URI(repositoryUrl), repositoryType)
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

    internal fun createLocalIvyRepository(repositoryName: String, action: IvyRepositoryAction = {}) = repositories.ivy {
        name = repositoryName

        // Location of Ivy files generated for the current project.
        val localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory)
        ivyPattern("${localPlatformArtifactsPath.pathString}/[organization]-[module]-[revision].[ext]")

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
                includeGroup(Configurations.Dependencies.BUNDLED_MODULE_GROUP)
                includeGroup(Configurations.Dependencies.BUNDLED_PLUGIN_GROUP)
                includeGroup(Configurations.Dependencies.LOCAL_IDE_GROUP)
                includeGroup(Configurations.Dependencies.LOCAL_PLUGIN_GROUP)
                includeGroup(Configurations.Dependencies.LOCAL_JETBRAINS_RUNTIME_GROUP)
            }
        }
        action()
    }
}
