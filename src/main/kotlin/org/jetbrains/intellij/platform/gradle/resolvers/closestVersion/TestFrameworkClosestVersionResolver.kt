// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.closestVersion

import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.extensions.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import java.net.URL

class TestFrameworkClosestVersionResolver(private val productInfo: ProductInfo, type: TestFrameworkType) : ClosestVersionResolver(
    subject = "Test Framework",
    url = URL("${Locations.INTELLIJ_REPOSITORY}/releases/${type.coordinates.groupId.replace('.', '/')}/${type.coordinates.artifactId}/maven-metadata.xml"),
) {

    override fun resolve() = inMaven(productInfo.buildNumber.toVersion())
}
