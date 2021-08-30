package org.jetbrains.intellij.utils

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import java.io.File
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
        val repositories = repositoriesBlock.invoke(repositoryHandler)

        try {
            return configurationContainer.detachedConfiguration(dependency).files.toList()
        } catch (e: Exception) {
            org.jetbrains.intellij.error(context, "Error when resolving dependency: $dependency", e)
            throw e
        } finally {
            repositoryHandler.removeAll(repositories)
        }
    }
}
