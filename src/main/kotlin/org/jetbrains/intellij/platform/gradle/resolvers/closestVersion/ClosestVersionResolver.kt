// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.closestVersion

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.model.MavenMetadata
import org.jetbrains.intellij.platform.gradle.model.XmlExtractor
import org.jetbrains.intellij.platform.gradle.resolvers.Resolver
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.throwIfNull
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import java.net.URL

/**
 * Interface for resolving the closest [Version] of any entity to the provided value.
 *
 * @param subject The name of the resource
 * @param url The URL where versions list is available for parsing
 */
abstract class ClosestVersionResolver(
    protected val subject: String,
    protected val url: String,
) : Resolver<Version> {

    private val log = Logger(ClosestVersionResolver::class.java)

    /**
     * Resolves the closest version to the provided [version] of the artifact available in Maven repository.
     *
     * @throws GradleException
     */
    @Throws(GradleException::class)
    protected fun inMaven(version: Version): Version {
        log.debug(message = "Resolving the $subject version closest to $version")
        return URL(url).openStream().use { inputStream ->
            XmlExtractor<MavenMetadata>().unmarshal(inputStream)
                .versioning
                ?.versions
                .throwIfNull { GradleException("Cannot resolve the $subject version closest to $version") }
                .map { it.toVersion() }
                .filter { it <= version }
                .maxOf { it }
        }
    }
}
