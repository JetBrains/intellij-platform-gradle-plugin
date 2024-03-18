// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.closestVersion

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.MavenMetadata
import org.jetbrains.intellij.platform.gradle.models.decode
import org.jetbrains.intellij.platform.gradle.resolvers.Resolver
import org.jetbrains.intellij.platform.gradle.utils.*
import java.net.URL

/**
 * Interface for resolving the closest [Version] of any entity to the provided value.
 *
 * @param urls The URLs list where versions list is available for parsing
 */
abstract class ClosestVersionResolver(
    protected val urls: List<URL>,
) : Resolver<Version> {

    override val subjectInput = urls.joinToString(";")

    /**
     * Resolves the closest version to the provided [version] of the artifact available in Maven repository.
     *
     * @throws GradleException
     */
    @Throws(GradleException::class)
    protected fun inMaven(version: Version) = urls
        .also { log.debug("Resolving the $subject version closest to $version") }
        .asSequence()
        .mapNotNull { runCatching { decode<MavenMetadata>(it) }.getOrNull() }
        .flatMap { it.versioning?.versions?.asSequence().orEmpty() }
        .map { it.toVersion() }
        .filter { it <= version }
        .maxOrNull()
        .also { it?.let { log.debug("Resolved $subject closest version: $it") } }
        ?: throw GradleException("Cannot resolve the $subject version closest to $version")
}

internal fun createMavenMetadataUrl(repositoryUrl: String, coordinates: Coordinates): URL {
    val path = coordinates.toString().replace(':', '/').replace('.', '/')
    return URL("$repositoryUrl/$path/maven-metadata.xml")
}
