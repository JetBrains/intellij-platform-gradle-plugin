// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.repositories

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor
import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import org.jetbrains.intellij.platform.gradle.shim.Shim
import java.net.URI

/**
 * Represents a base class for artifact repositories used with [Shim].
 *
 * @param name The name of the repository.
 * @param url The URL of the repository.
 * @param allowInsecureProtocol Flag indicating if insecure protocols (like http) are allowed.
 */
abstract class BaseArtifactRepository(
    private val name: String,
    private val url: URI,
    private val allowInsecureProtocol: Boolean,
) : ArtifactRepository, UrlArtifactRepository {

    override fun getName() = name

    @Throws(UnsupportedOperationException::class)
    override fun setName(name: String) = throw UnsupportedOperationException()

    override fun getUrl() = url

    @Throws(UnsupportedOperationException::class)
    override fun setUrl(url: URI) = throw UnsupportedOperationException()

    @Throws(UnsupportedOperationException::class)
    override fun setUrl(url: Any) = throw UnsupportedOperationException()

    override fun isAllowInsecureProtocol() = allowInsecureProtocol

    @Throws(UnsupportedOperationException::class)
    override fun setAllowInsecureProtocol(allowInsecureProtocol: Boolean) = throw UnsupportedOperationException()

    @Throws(UnsupportedOperationException::class)
    override fun content(configureAction: Action<in RepositoryContentDescriptor>) = throw UnsupportedOperationException()
}
