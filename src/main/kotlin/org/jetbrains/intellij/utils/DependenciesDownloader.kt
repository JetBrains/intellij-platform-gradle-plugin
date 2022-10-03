// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.utils

import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.IntelliJPluginConstants.ANDROID_STUDIO_PRODUCTS_RELEASES_URL
import org.jetbrains.intellij.error
import org.jetbrains.intellij.repositoryVersion
import java.io.File
import java.net.URI
import javax.inject.Inject

internal abstract class DependenciesDownloader @Inject constructor(
    private val configurationContainer: ConfigurationContainer,
    private val dependencyHandler: DependencyHandler,
    private val repositoryHandler: RepositoryHandler,
) {

    fun downloadFromRepository(
        context: String?,
        dependenciesBlock: DependencyHandler.() -> Dependency,
        repositoriesBlock: (RepositoryHandler.() -> ArtifactRepository)? = null,
        silent: Boolean = false,
    ) = downloadFromMultipleRepositories(context, dependenciesBlock, {
        listOfNotNull(repositoriesBlock?.invoke(this))
    }, silent)

    fun downloadFromMultipleRepositories(
        context: String?,
        dependenciesBlock: DependencyHandler.() -> Dependency,
        repositoriesBlock: RepositoryHandler.() -> List<ArtifactRepository>,
        silent: Boolean = false,
    ): List<File> {
        val dependency = dependenciesBlock(dependencyHandler)
        val baseRepositories = with(repositoryHandler) {
            toList().also { base ->
                // Remove common repositories from the beginning of the list and keep project custom ones only
                removeIf {
                    it is MavenArtifactRepository && listOf(
                        ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME,
                        DefaultRepositoryHandler.GRADLE_PLUGIN_PORTAL_REPO_NAME,
                        DefaultRepositoryHandler.DEFAULT_BINTRAY_JCENTER_REPO_NAME,
                        DefaultRepositoryHandler.GOOGLE_REPO_NAME,
                    ).contains(it.name)
                }

                // Add custom plugin repositories after project custom repositories
                repositoriesBlock(this)

                // Add common project repositories after to the end of the list
                addAll(base)

                // Ensure MavenCentral is available at the end of the list
                if (none { it is MavenArtifactRepository && it.name == ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME }) {
                    mavenCentral()
                }
            }
        }

        try {
            return configurationContainer.detachedConfiguration(dependency).files.toList()
        } catch (e: Exception) {
            if (!silent) {
                error(context, "Error when resolving dependency: $dependency", e)
            }
            throw e
        } finally {
            with(repositoryHandler) {
                // Revert to the original project repositories
                clear()
                addAll(baseRepositories)
            }
        }
    }
}

internal fun RepositoryHandler.ivyRepository(
    repositoryUrl: String,
    pattern: String = "",
    block: (IvyArtifactRepository.() -> Unit)? = null,
) =
    ivy {
        url = URI(repositoryUrl)
        patternLayout { artifact(pattern) }
        metadataSources { artifact() }
        block?.invoke(this)
    }

internal fun RepositoryHandler.mavenRepository(repositoryUrl: String, block: (MavenArtifactRepository.() -> Unit)? = null) =
    maven {
        url = URI(repositoryUrl)
        block?.invoke(this)
    }

internal fun DependenciesDownloader.getAndroidStudioReleases(context: String?) = downloadFromRepository(context, {
    create(
        group = "org.jetbrains",
        name = "android-studio-products-releases",
        version = repositoryVersion,
        ext = "xml",
    )
}, {
    ivyRepository(ANDROID_STUDIO_PRODUCTS_RELEASES_URL)
}).first().canonicalPath
