// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor
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
import org.jetbrains.intellij.platform.gradle.Constants.Repositories
import org.jetbrains.intellij.platform.gradle.CustomPluginRepositoryType
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.artifacts.repositories.PluginArtifactRepository
import org.jetbrains.intellij.platform.gradle.flow.StopShimServerAction
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.models.toBundledIvyArtifactsRelativeTo
import org.jetbrains.intellij.platform.gradle.services.ShimManagerService
import org.jetbrains.kotlin.util.removeSuffixIfPresent
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.Path
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

                this.content {
                    excludeBundledModuleAndPluginGroups()
                }

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

        this.content {
            excludeBundledModuleAndPluginGroups()
        }

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

            this.content {
                excludeBundledModuleAndPluginGroups()
            }
        }.apply(action)

        return repository
    }

    /**
     * @see org.jetbrains.intellij.platform.gradle.models.IvyModule.toAbsolutePathIvyArtifact
     * @see org.jetbrains.intellij.platform.gradle.models.IvyModule.toAbsolutePathLocalPluginIvyArtifacts
     */
    internal fun createLocalIvyRepository(repositoryName: String, action: IvyRepositoryAction = {}): IvyArtifactRepository {
        // The contract is that we are working with absolute normalized paths here.
        val ivyLocationPath = providers.localPlatformArtifactsPath(rootProjectDirectory).absolute().normalize()

        return createExclusiveIvyRepository(
            repositoryName,
            setOf(
                Dependencies.LOCAL_IDE_GROUP,
                Dependencies.LOCAL_PLUGIN_GROUP,
                Dependencies.LOCAL_JETBRAINS_RUNTIME_GROUP
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

        // For performance reasons make it exclusive.
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
     * @see org.jetbrains.intellij.platform.gradle.models.IvyModule.toAbsolutePathIvyArtifact
     * @see org.jetbrains.intellij.platform.gradle.models.IvyModule.toBundledIvyArtifactsRelativeTo
     * @see org.jetbrains.intellij.platform.gradle.models.IvyModule.toAbsolutePathLocalPluginIvyArtifacts
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
            .removeSuffixIfPresent("/")

        ivyPattern("$ivyPath/[organization]-[module]-[revision].[ext]")

        // If the path is not given, we expect the "artifact" (IvyModule#name) to contain the absolute paths
        // In different situations it may point to a completely unrelated locations on the file system.

        // It has to be prefixed by "/" because if this pattern is not a fully-qualified URL, it will be interpreted
        // as a file relative to the project directory, even if the "artifact" (IvyModule#name) starts with a "/".
        // We can not rid of this leading separator as long as the repository created by createLocalIvyRepository
        // or (localPlatformArtifacts() in public API) is used to reference completely unrelated paths. If they were
        // split into multiple repositories, each could be prefixed with an absolute path specific to that repo.

        // In this case, name is the full path (without the leading "/"), so type is not needed.
        // In this case, it should be ok to have an absolute path in the name, because this is a fallback method,
        // which called for directories only (at least at the moment of writing this), where it is not clear how
        // to split it into two pieces: path and artifact.
        // See IntelliJPlatformRepositoriesHelper.createIvyArtifactRepository on why having abs path in name may be bad.
        val artifactPattern = "/[artifact]"
        artifactPattern(artifactPattern)

        /**
         * Because artifact paths always start with `/` (see [toPublication] for details),
         * on Windows, we have to guess to which drive letter the artifact path belongs to.
         * To do so, we add all drive letters (`a:/[artifact]`, `b:/[artifact]`, `c:/[artifact]`, ...) to the stack,
         * starting with `c` for the sake of micro-optimization.
         *
         * This is not needed in "createDynamicBundledIvyArtifactsRepository",
         * where the artifact path is given, because it is already prefixed.
         */
        if (OperatingSystem.current().isWindows) {
            (('c'..'z') + 'a' + 'b').forEach { artifactPattern("$it:$artifactPattern") }
        }

        this.content {
            excludeBundledModuleAndPluginGroups()
        }
    }

    /**
     * Explicitly exclude these two groups from any declared repositories, because we do not expect
     * such artifacts to exist anywhere except in the repo created by:
     *
     * @see createDynamicBundledIvyArtifactsRepository
     */
    private fun RepositoryContentDescriptor.excludeBundledModuleAndPluginGroups() {
        this.excludeGroup(Dependencies.BUNDLED_MODULE_GROUP)
        this.excludeGroup(Dependencies.BUNDLED_PLUGIN_GROUP)
    }

    companion object {

        /**
         * Registers an Ivy repository containing IntelliJ Platform bundled plugins and modules.
         * @see Path.toBundledIvyArtifactsRelativeTo
         * @see org.jetbrains.intellij.platform.gradle.models.IvyModule.toAbsolutePathIvyArtifact
         * @see org.jetbrains.intellij.platform.gradle.models.IvyModule.toBundledIvyArtifactsRelativeTo
         * @see org.jetbrains.intellij.platform.gradle.models.IvyModule.toAbsolutePathLocalPluginIvyArtifacts
         * @see org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesHelper.registerIntellijPlatformIvyRepo
         */
        internal fun createDynamicBundledIvyArtifactsRepository(
            repositories: RepositoryHandler,
            ivyLocationPath: Path,
            artifactLocationPath: Path
        ) {
            val repositoryName = "${Repositories.LOCAL_INTELLI_J_PLATFORM_ARTIFACTS} (${artifactLocationPath.invariantSeparatorsPathString})"

            // It may be called more than once
            if (repositories.findByName(repositoryName) != null) {
                return
            }

            /**
             * We can't use [IntelliJPlatformRepositoriesHelper.createExclusiveIvyRepository] here because it would try to change already declared repositories,
             * and if any of them have been already "used" by something, it would throw an exception.
             * See: [org.gradle.api.internal.artifacts.repositories.DefaultRepositoryContentDescriptor.assertMutable]
             */
            repositories.forEach {
                try {
                    it.content {
                        /**
                         * For performance reasons exclude the group from already added repos, since we don't expect it to exist in any public repositories.
                         * The ones declared after shouldn't matter, as long as the artifact is found in this repository,
                         * because Gradle checks repositories in their declaration order.
                         * Tests on an env with removed caches show that this is actually necessary to prevent extra requests.
                         */
                        excludeGroup(Dependencies.BUNDLED_MODULE_GROUP)
                        excludeGroup(Dependencies.BUNDLED_PLUGIN_GROUP)
                        // Could be improved some day to the next, but today it fails on Gradle 8.2, works on 8.10.2
                        //excludeGroupAndSubgroups(Dependencies.BUNDLED_MODULE_GROUP)
                        //excludeGroupAndSubgroups(Dependencies.BUNDLED_PLUGIN_GROUP)
                    }
                } catch (e: Exception) {
                    // Ignore, don't care.
                }
            }

            repositories.ivy {
                name = repositoryName

                // The contract is that we are working with absolute normalized paths here.
                val absNormIvyLocationPath = ivyLocationPath.absolute().normalize()
                val absNormArtifactLocationPath = artifactLocationPath.absolute().normalize()

                // Location of Ivy files generated for the current project.
                val ivyPath = absNormIvyLocationPath
                    .absolute()
                    .normalize()
                    .invariantSeparatorsPathString
                    .removeSuffixIfPresent("/")

                ivyPattern("$ivyPath/[organization]-[module]-[revision].[ext]")

                // If the path is given, we expect:
                // - All artifacts in this repo are stored inside artifactLocationPath and the repo is never used for
                //   any paths outside optionalPath. See IvyModuleKt.toBundledIvyArtifactsRelativeTo
                // - The above allows us to generate artifacts paths relative to absNormArtifactLocationPath.
                // - Relative paths are better than absolute because if Gradle's dependency verification is used with
                //   metadata (e.g. ivy.ml or pom.xml) files verification enabled, hashes of these files will be the
                //   same on different environments, despite that they are stored in different locations. If absolute
                //   paths are used, they will be mentioned in ivy.xml thus changing the hash on each env.
                //
                // - "type" (IvyModule#type) may contain an optional path, relative to the artifactLocationPath, without
                //   the file's name.
                //
                //   Type is not the best to field to put this into, but there is no alternative because "url" field
                //   does not work in Gradle since its IvyArtifact class does not even have "url" in it.
                //
                //   The reason why we put the path into type is that the name should not have it, because:
                //       - Artifact name may come up in files like Gradle's verification-metadata.xml which will make
                //         them not portable between different environments.
                //         https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1778
                //         https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1779
                //         https://docs.gradle.org/current/userguide/dependency_verification.html
                //
                //       - Artifact name may also come up in Gradle errors, e.g. if for some reason the artifact is not
                //         resolved. In that case the artifact coordinates may look very weird like:
                //         bundledPlugin:/some/path/more/path/some.jar:123.456.789
                //         For the same reason file extension is also stored in "ext".
                //
                // - "artifact" (IvyModule#name) is mandatory and contains the second part of the path, which is file
                //   name, without the extension.
                //
                // - "ext" (IvyModule#ext) is an optional file extension, e.g. "jar". It is optional only because files
                //   do not always have extensions. For directories, it would be "directory", but, in this case we never
                //   expect to have a directory, always only files.
                val optionalPath = absNormArtifactLocationPath.invariantSeparatorsPathString.removeSuffixIfPresent("/")
                artifactPattern("$optionalPath/([type]/)[artifact](.[ext])")

                content {
                    includeGroup(Dependencies.BUNDLED_MODULE_GROUP)
                    includeGroup(Dependencies.BUNDLED_PLUGIN_GROUP)
                    // Could be improved some day to the next, but today it fails on Gradle 8.2, works on 8.10.2
                    //includeGroupAndSubgroups(Dependencies.BUNDLED_MODULE_GROUP)
                    //includeGroupAndSubgroups(Dependencies.BUNDLED_PLUGIN_GROUP)
                }
            }
        }
    }
}
