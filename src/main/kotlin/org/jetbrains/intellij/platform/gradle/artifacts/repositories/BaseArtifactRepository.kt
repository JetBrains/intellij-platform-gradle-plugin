// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.repositories

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor
import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import java.net.URI

abstract class BaseArtifactRepository(
    private val name: String,
    private val url: URI,
    private val allowInsecureProtocol: Boolean,
) : ArtifactRepository, UrlArtifactRepository {

    override fun getName() = name

    override fun setName(name: String) = throw UnsupportedOperationException()

    override fun getUrl() = url

    override fun setUrl(url: URI) = throw UnsupportedOperationException()

    override fun setUrl(url: Any) = throw UnsupportedOperationException()

    override fun isAllowInsecureProtocol() = allowInsecureProtocol

    override fun setAllowInsecureProtocol(allowInsecureProtocol: Boolean) = throw UnsupportedOperationException()

    override fun content(configureAction: Action<in RepositoryContentDescriptor>) = throw UnsupportedOperationException()
}
