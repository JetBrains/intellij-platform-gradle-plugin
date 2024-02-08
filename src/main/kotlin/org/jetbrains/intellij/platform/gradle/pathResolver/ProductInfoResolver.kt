// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.pathResolver

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.throwIfNull
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private const val PRODUCT_INFO_NAME = "product-info.json"

/**
 * Resolves the path to the `product-info.json` file for the given [Path].
 * This resolver relies on [listDirectoryEntries] instead of using the [exists] due to the Gradle configuration cache issues.
 */
class ProductInfoResolver(
    private val intellijPlatformDirectory: Path,
) : PathResolver {

    private val log = Logger(javaClass)

    override fun resolve() = listOf(
        {
            intellijPlatformDirectory.takeIf { it.name == PRODUCT_INFO_NAME }
        },
        {
            intellijPlatformDirectory
                .listDirectoryEntries(PRODUCT_INFO_NAME)
                .firstOrNull()
        },
        {
            intellijPlatformDirectory
                .listDirectoryEntries("Resources")
                .firstOrNull()
                ?.listDirectoryEntries(PRODUCT_INFO_NAME)
                ?.firstOrNull()
        },
    )
        .asSequence()
        .mapNotNull { it() }
        .firstOrNull()
        ?.also { log.info("Resolved $PRODUCT_INFO_NAME file: $it") }
        .throwIfNull { GradleException("No $PRODUCT_INFO_NAME file found") }
}
