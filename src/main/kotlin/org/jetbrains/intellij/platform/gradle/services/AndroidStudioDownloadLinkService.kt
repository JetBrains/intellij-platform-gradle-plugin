// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.intellij.platform.gradle.models.AndroidStudioReleases
import org.jetbrains.intellij.platform.gradle.providers.AndroidStudioDownloadLinkValueSource
import org.jetbrains.intellij.platform.gradle.providers.loadAndroidStudioReleases
import org.jetbrains.intellij.platform.gradle.providers.resolveDownloadLink
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

abstract class AndroidStudioDownloadLinkService : BuildService<BuildServiceParameters.None> {

    private val log = Logger(javaClass)
    private val releases = ConcurrentHashMap<String, AndroidStudioReleases>()

    internal fun resolve(
        parameters: AndroidStudioDownloadLinkValueSource.Parameters,
        loader: (String) -> String? = { URI(it).toURL().readText() },
    ) = runCatching {
        val androidStudioUrl = parameters.androidStudioUrl.orNull
        val androidStudioReleases = androidStudioUrl?.let {
            releases.computeIfAbsent(it) { url ->
                requireNotNull(loadAndroidStudioReleases(url, loader, log)) {
                    "Failed to decode Android Studio releases from: $url"
                }
            }
        }
        requireNotNull(androidStudioReleases) { "Failed to decode Android Studio releases from: ${parameters.androidStudioUrl.orNull}" }

        androidStudioReleases.resolveDownloadLink(parameters.androidStudioVersion.orNull)
    }.onFailure {
        log.error("${javaClass.canonicalName} execution failed.", it)
    }.getOrNull()
}
