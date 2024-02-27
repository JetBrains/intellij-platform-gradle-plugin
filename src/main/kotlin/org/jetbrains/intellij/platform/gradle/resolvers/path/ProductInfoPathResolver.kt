// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private const val PRODUCT_INFO_NAME = "product-info.json"

/**
 * Resolves the path to the `product-info.json` file within the given [Path].
 * This resolver relies on [listDirectoryEntries] instead of using the [exists] due to the Gradle configuration cache issues.
 */
class ProductInfoPathResolver(
    private val intellijPlatformDirectory: Path,
) : PathResolver(
    subject = PRODUCT_INFO_NAME,
) {

    override val predictions: Sequence<Pair<String, () -> Path?>>
        get() = sequenceOf(
            /**
             * Check if [intellijPlatformDirectory] is, by any reason, our [PRODUCT_INFO_NAME].
             */
            PRODUCT_INFO_NAME to {
                intellijPlatformDirectory
                    .takeIf { it.name == PRODUCT_INFO_NAME }
            },
            /**
             * Check if [PRODUCT_INFO_NAME] is located directly in [intellijPlatformDirectory].
             */
            PRODUCT_INFO_NAME to {
                intellijPlatformDirectory
                    .listDirectoryEntries(PRODUCT_INFO_NAME)
                    .firstOrNull()
            },
            /**
             * Check if [PRODUCT_INFO_NAME] is located directly in `[intellijPlatformDirectory]/Resources`.
             */
            PRODUCT_INFO_NAME to {
                intellijPlatformDirectory
                    .listDirectoryEntries("Resources")
                    .firstOrNull()
                    ?.listDirectoryEntries(PRODUCT_INFO_NAME)
                    ?.firstOrNull()
            },
        )
}
