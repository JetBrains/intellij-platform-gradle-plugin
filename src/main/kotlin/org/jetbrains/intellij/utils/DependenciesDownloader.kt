// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.utils

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.jetbrains.intellij.error
import java.io.File
import java.net.URI
import javax.inject.Inject

open class DependenciesDownloader @Inject constructor(
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
        val dependency = dependenciesBlock.invoke(dependencyHandler)
        val repositories = repositoriesBlock.invoke(repositoryHandler) + repositoryHandler.mavenCentral()

        try {
            return configurationContainer.detachedConfiguration(dependency).files.toList()
        } catch (e: Exception) {
            if (!silent) {
                error(context, "Error when resolving dependency: $dependency", e)
            }
            throw e
        } finally {
            repositoryHandler.removeAll(repositories.toSet())
        }
    }
}

internal fun RepositoryHandler.ivyRepository(repositoryUrl: String, pattern: String = "") =
    ivy {
        url = URI(repositoryUrl)
        patternLayout { artifact(pattern) }
        metadataSources { artifact() }
    }

internal fun RepositoryHandler.mavenRepository(repositoryUrl: String) =
    maven {
        url = URI(repositoryUrl)
    }
