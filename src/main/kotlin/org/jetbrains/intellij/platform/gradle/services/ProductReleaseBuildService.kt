// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.intellij.platform.gradle.models.JetBrainsCdnBuilds
import org.jetbrains.intellij.platform.gradle.providers.ProductReleaseBuildValueSource
import org.jetbrains.intellij.platform.gradle.providers.loadProductReleaseBuilds
import org.jetbrains.intellij.platform.gradle.providers.resolveBuild
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

abstract class ProductReleaseBuildService : BuildService<BuildServiceParameters.None> {

    private val log = Logger(javaClass)
    private val builds = ConcurrentHashMap<String, List<JetBrainsCdnBuilds>>()

    internal fun resolve(
        parameters: ProductReleaseBuildValueSource.Parameters,
        loader: (String) -> String? = { URI(it).toURL().readText() },
    ) = with(parameters) {
        val buildsUrl = productsReleasesCdnBuildsUrl.orNull
            ?.replace("{type}", type.get().code)
            ?: return@with null

        builds
            .computeIfAbsent(buildsUrl) { url ->
                requireNotNull(loadProductReleaseBuilds(url, loader, log)) {
                    "Failed to decode JetBrains IDE releases from URL: $url"
                }
            }
            .resolveBuild(version.get())
    }
}
