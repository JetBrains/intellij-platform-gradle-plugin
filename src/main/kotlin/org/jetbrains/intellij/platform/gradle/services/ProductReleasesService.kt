// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.providers.ProductReleasesValueSource
import org.jetbrains.intellij.platform.gradle.providers.loadProductReleases
import org.jetbrains.intellij.platform.gradle.providers.toNotations
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

abstract class ProductReleasesService : BuildService<BuildServiceParameters.None> {

    private val log = Logger(javaClass)
    private val releases = ConcurrentHashMap<Pair<String?, String?>, List<ProductRelease>>()

    internal fun resolve(
        parameters: ProductReleasesValueSource.Parameters,
        loader: (String) -> String? = { URI(it).toURL().readText() },
    ) = releases
        .computeIfAbsent(parameters.jetbrainsIdesUrl.orNull to parameters.androidStudioUrl.orNull) { (jetbrainsIdesUrl, androidStudioUrl) ->
            loadProductReleases(jetbrainsIdesUrl, androidStudioUrl, loader, log)
        }
        .toNotations(parameters, log)
}
