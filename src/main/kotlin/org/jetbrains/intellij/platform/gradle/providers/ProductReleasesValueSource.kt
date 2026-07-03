// Copyright 2000-2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel
import org.jetbrains.intellij.platform.gradle.tasks.PrintProductsReleasesTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.intellij.platform.gradle.utils.Logger

/**
 * Provides a complete list of binary IntelliJ Platform product releases matching the given [FilterParameters] criteria.
 *
 * Its main purpose is to feed the IntelliJ Plugin Verifier with a list of all compatible IDEs for the binary plugin verification.
 *
 * @see PrintProductsReleasesTask
 * @see VerifyPluginTask
 * @see GradleProperties.ProductsReleasesCdnBuildsUrl
 * @see GradleProperties.ProductsReleasesAndroidStudioUrl
 */
abstract class ProductReleasesValueSource : ValueSource<List<String>, ProductReleasesValueSource.FilterParameters> {

    interface FilterParameters : ValueSourceParameters {
        /**
         * The build number from which the binary IDE releases will be matched.
         */
        @get:Input
        @get:Optional
        val sinceBuild: Property<String>

        /**
         * Build number until which the binary IDE releases will be matched.
         */
        @get:Input
        @get:Optional
        val untilBuild: Property<String>

        /**
         * A list of [IntelliJPlatformType] types to match.
         */
        @get:Input
        @get:Optional
        val types: ListProperty<IntelliJPlatformType>

        /**
         * A list of [Channel] types of binary releases to search in.
         */
        @get:Input
        @get:Optional
        val channels: ListProperty<Channel>
    }

    private val log = Logger(javaClass)

    override fun obtain() = emptyList<String>()

//        loadProductReleases(
//        types = parameters.types.get(),
//        loader = { URI(it).toURL().readText() },
//        log = log,
//    ).toNotations(parameters, log)
}

//internal fun loadProductReleases(
//    types: List<IntelliJPlatformType>,
//    loader: (String) -> String?,
//    log: Logger,
//): List<ProductRelease> =
//    loadProductReleaseCatalogEntries(productsReleasesCdnBuildsUrl, androidStudioUrl, types, loader, log)
//        .map { it.toProductRelease() }


