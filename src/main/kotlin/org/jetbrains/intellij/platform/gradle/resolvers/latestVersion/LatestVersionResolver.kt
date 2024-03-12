// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.latestVersion

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.models.MavenMetadata
import org.jetbrains.intellij.platform.gradle.models.decode
import org.jetbrains.intellij.platform.gradle.resolvers.Resolver
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import java.net.HttpURLConnection
import java.net.URL

/**
 * Interface for resolving the latest [Version] of any entity.
 *
 * @param subject The name of the resource
 * @param url The URL where versions list is available for parsing
 */
abstract class LatestVersionResolver(
    protected val url: URL,
) : Resolver<Version> {


    /**
     * Resolves the latest version of the artifact available in Maven repository.
     *
     * @throws GradleException
     */
    @Throws(GradleException::class)
    protected fun fromMaven(): Version {
        log.debug(message = "Resolving the latest '$subject' version from: $url")
        return decode<MavenMetadata>(url)
            ?.versioning
            ?.latest
            ?.toVersion()
            ?: throw GradleException("Cannot resolve the latest '$subject' version from: $url")
    }

    /**
     * Resolves the latest version of the artifact available in Maven repository.
     *
     * @throws GradleException
     */
    @Suppress("SameParameterValue")
    @Throws(GradleException::class)
    protected fun fromGitHub(): Version {
        log.debug(message = "Resolving the latest '$subject' version from: $url")
        try {
            return URL("$url/releases/latest").openConnection().run {
                (this as HttpURLConnection).instanceFollowRedirects = false
                getHeaderField("Location").split('/').last().removePrefix("v").toVersion()
            }
        } catch (e: Exception) {
            throw GradleException("Cannot resolve the latest '$subject' version from: $url", e)
        }
    }
}
