// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.maven
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Extensions
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Locations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import java.net.URI
import javax.inject.Inject

internal typealias RepositoryAction = (ArtifactRepository.() -> Unit)

@Suppress("unused")
@IntelliJPlatform
abstract class IntelliJPlatformRepositoriesExtension @Inject constructor(
    private val repositories: RepositoryHandler,
    private val providers: ProviderFactory,
) {

    fun releases(action: RepositoryAction = {}) = createRepository(
        name = "IntelliJ Repository",
        url = "https://www.jetbrains.com/intellij-repository/releases",
        urlWithCacheRedirector = "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases",
        action = action,
    )

    fun snapshots(action: RepositoryAction = {}) = createRepository(
        name = "IntelliJ Repository (Snapshots)",
        url = "https://www.jetbrains.com/intellij-repository/snapshots",
        urlWithCacheRedirector = "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/snapshots",
        action = action,
    )

    fun nightly(action: RepositoryAction = {}) = createRepository(
        name = "IntelliJ Repository (Nightly)",
        url = "https://www.jetbrains.com/intellij-repository/nightly",
        urlWithCacheRedirector = "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/nightly",
        action = action,
    )

    fun marketplace(action: RepositoryAction = {}) = createRepository(
        name = "JetBrains Marketplace Repository",
        url = "https://plugins.jetbrains.com/maven",
        urlWithCacheRedirector = "https://cache-redirector.jetbrains.com/plugins.jetbrains.com/maven",
        action = action,
    )

    fun jetbrainsRuntime(action: RepositoryAction = {}) = createIvyRepository(
        name = "JetBrains Runtime",
        url = Locations.JETBRAINS_RUNTIME_REPOSITORY,
        pattern = "[revision].tar.gz",
        action = action,
    )

    // TODO: migrate to Maven Central
    fun pluginVerifier(action: RepositoryAction = {}) = createRepository(
        name = "IntelliJ Plugin Verifier Repository",
        url = "https://packages.jetbrains.team/maven/p/intellij-plugin-verifier/intellij-plugin-verifier",
        urlWithCacheRedirector = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/intellij-plugin-verifier/intellij-plugin-verifier",
        action = action,
    )

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
    fun ivy(action: RepositoryAction = {}) = repositories.ivy {
        ivyPattern(".gradle/intellijPlatform/ivy/[organization]-[module]-[revision].[ext]")
        artifactPattern("/[artifact]")
    }.apply {
        repositories.exclusiveContent {
            forRepositories(this@apply)
            filter {
                includeGroup(PLUGIN_GROUP_NAME)
//                IntelliJPlatformType.values()
//                    .forEach { type ->
//                        type.dependency.let {
//                            includeModule(it.group, it.name)
//                            includeGroupByRegex("${it.group}-${it.name}".replace(".", "\\.") + ".*")
//                        }
//                        type.binary?.let {
//                            if (type.dependency != it) {
//                                includeModule(it.group, it.name)
//                                includeGroupByRegex("${it.group}-${it.name}".replace(".", "\\.") + ".*")
//                            }
//                        }
//                    }
            }
        }
        action()
    }

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

fun RepositoryHandler.intellijPlatform(configure: Action<IntelliJPlatformRepositoriesExtension>) =
    (this as ExtensionAware).extensions.configure(Extensions.INTELLIJ_PLATFORM, configure)
