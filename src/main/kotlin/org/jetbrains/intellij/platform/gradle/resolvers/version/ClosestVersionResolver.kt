// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.version

import org.gradle.api.GradleException
import org.gradle.api.resources.ResourceHandler
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.Version
import java.net.URL

/**
 * Interface for resolving the closest [Version] of any entity to the provided value.
 *
 * @param urls The URLs list where the version list is available for parsing
 */
class ClosestVersionResolver(
    subject: String,
    coordinates: Coordinates,
    val version: Version,
    urls: List<URL>,
    resources: ResourceHandler? = null,
) : VersionResolver(subject, coordinates, urls, resources) {

    private val log = Logger(javaClass)

    override fun resolve(): Version {
        log.debug("Resolving the $subject version closest to $version")

        return collectVersions()
            .ifEmpty {
                throw GradleException(
                    """
                    Cannot resolve the $subject version closest to: $version
                    Please ensure there are necessary repositories present in the project repositories section where the `$coordinates` artifact is published, i.e., by adding the `defaultRepositories()` entry.
                    See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
                    """.trimIndent()
                )
            }
            .filter { it <= version }
            .maxOrNull()
            .also { it?.let { log.debug("Resolved $subject closest version: $it") } }
            ?: throw GradleException("Cannot resolve the $subject version closest to: $version")
    }
}
