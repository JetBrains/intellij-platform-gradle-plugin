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
    ) = downloadFromMultipleRepositories(context, dependenciesBlock) {
        listOfNotNull(repositoriesBlock?.invoke(this))
    }

    fun downloadFromMultipleRepositories(
        context: String?,
        dependenciesBlock: DependencyHandler.() -> Dependency,
        repositoriesBlock: RepositoryHandler.() -> List<ArtifactRepository>,
    ): List<File> {
        val dependency = dependenciesBlock.invoke(dependencyHandler)
        val repositories = repositoriesBlock.invoke(repositoryHandler) + repositoryHandler.mavenCentral()

        try {
            return configurationContainer.detachedConfiguration(dependency).files.toList()
        } catch (e: Exception) {
            error(context, "Error when resolving dependency: $dependency", e)
            throw e
        } finally {
            repositoryHandler.removeAll(repositories.toSet())
        }
    }
}

internal fun DependencyHandler.create(
    group: String,
    name: String,
    version: String?,
    classifier: String? = null,
    extension: String? = null,
    configuration: String? = null,
): Dependency = create(mapOf(
    "group" to group,
    "name" to name,
    "version" to version,
    "classifier" to classifier,
    "ext" to extension,
    "configuration" to configuration,
))

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
