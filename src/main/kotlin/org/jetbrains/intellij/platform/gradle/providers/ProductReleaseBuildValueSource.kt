// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.JetBrainsCdnBuilds
import org.jetbrains.intellij.platform.gradle.models.decode
import org.jetbrains.intellij.platform.gradle.models.json
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.net.URI

/**
 * Provides a build number for the given IntelliJ Platform product release using its type and version.
 *
 * @see GradleProperties.ProductsReleasesCdnBuildsUrl
 */
abstract class ProductReleaseBuildValueSource : ValueSource<String, ProductReleaseBuildValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        /**
         * The URL to the resource containing the XML with all JetBrains IDEs releases.
         *
         * @see GradleProperties.ProductsReleasesCdnBuildsUrl
         */
        @get:Input
        val productsReleasesCdnBuildsUrl: Property<String>

        /**
         * The release version to query.
         */
        @get:Input
        val version: Property<String>

        /**
         * The release [IntelliJPlatformType] to query.
         */
        @get:Input
        val type: Property<IntelliJPlatformType>
    }

    private val log = Logger(javaClass)

    override fun obtain() = with(parameters) {
        val cdnBuildsContent = productsReleasesCdnBuildsUrl.orNull
            ?.replace("{type}", type.get().code)
            ?.let { URI(it).toURL().readText() }
        val jetbrainsIdesReleases = cdnBuildsContent
            ?.also { log.info("Reading JetBrains IDEs releases from URL: ${productsReleasesCdnBuildsUrl.orNull}") }
            ?.let { decode<List<JetBrainsCdnBuilds>>(it, stringFormat = json) }
            ?.firstOrNull()
            ?: return@with null

        jetbrainsIdesReleases.releases.find { it.version == version.get() }?.build
    }
}
