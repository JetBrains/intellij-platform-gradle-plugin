// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.closestVersion

import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.utils.toVersion

class TestFrameworkClosestVersionResolver(
    private val productInfo: ProductInfo,
    repositoryUrls: List<String>,
    coordinates: Coordinates,
) : ClosestVersionResolver(
    urls = repositoryUrls.map { url -> createMavenMetadataUrl(url, coordinates) }
) {

    override val subject = "Test Framework"

    override fun resolve() = inMaven(productInfo.buildNumber.toVersion())
}
