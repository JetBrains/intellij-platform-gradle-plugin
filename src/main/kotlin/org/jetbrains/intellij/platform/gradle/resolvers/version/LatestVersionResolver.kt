// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.version

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.Version
import java.net.URL

/**
 * Interface for resolving the latest [Version] of any entity.
 *
 * @param subject The name of the resource
 * @param urls The URLs where versions list is available for parsing
 */
class LatestVersionResolver(
    subject: String,
    coordinates: Coordinates,
    urls: List<URL>,
) : VersionResolver(subject, coordinates, urls) {

    private val log = Logger(javaClass)

    override fun resolve(): Version {
        log.debug("Resolving the $subject latest version")

        return collectVersions()
            .ifEmpty {
                throw GradleException(
                    """
                    Cannot resolve the $subject latest version
                    Please ensure there are necessary repositories present in the project repositories section where the `$coordinates` artifact is published, i.e., by adding the `defaultRepositories()` entry.
                    See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
                    """.trimIndent()
                )
            }
            .maxOrNull()
            .also { it?.let { log.debug("Resolved $subject closest version: $it") } }
            ?: throw GradleException("Cannot resolve the $subject latest version")
    }
}
