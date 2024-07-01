// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.version

import org.gradle.api.resources.ResourceHandler
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.MavenMetadata
import org.jetbrains.intellij.platform.gradle.models.decode
import org.jetbrains.intellij.platform.gradle.resolvers.Resolver
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.resolve
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import java.net.URL

abstract class VersionResolver (
    override val subject: String,
    val coordinates: Coordinates,
    val urls: List<URL>,
    val resources: ResourceHandler? = null,
) : Resolver<Version> {

    override val subjectInput = urls.joinToString(";")

    /**
     * Collects versions of the artifact available in Maven repositories.
     */
    internal fun collectVersions() = urls
        .asSequence()
        .map {
            val repository = it.toString().trimEnd('/')
            val path = coordinates.toString().replace(':', '/').replace('.', '/')
            "$repository/$path/maven-metadata.xml"
        }
        .mapNotNull { url ->
            runCatching {
                when {
                    resources != null -> resources.resolve(url)?.toPath()?.let { decode<MavenMetadata>(it) }
                    else -> URL(url).let { decode<MavenMetadata>(it) }
                }
            }.getOrNull()
        }
        .flatMap { it.versioning?.versions?.asSequence().orEmpty() }
        .map { it.toVersion() }
}
