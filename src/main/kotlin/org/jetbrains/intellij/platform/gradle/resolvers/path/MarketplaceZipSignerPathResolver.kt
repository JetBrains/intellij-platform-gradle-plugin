// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.nio.file.Path

/**
 * Resolves Marketplace ZIP Signer.
 *
 * @param marketplaceZipSigner The [Configurations.MARKETPLACE_ZIP_SIGNER] configuration.
 * @param localPath The local path to the Marketplace ZIP Signer file.
 */
class MarketplaceZipSignerPathResolver(
    val marketplaceZipSigner: FileCollection,
    val localPath: RegularFileProperty,
) : PathResolver(
    subject = "Marketplace ZIP Signer",
) {

    override val predictions: Sequence<Pair<String, () -> Path?>>
        get() = sequenceOf(
            "$subject specified with a local path" to {
                /**
                 * Checks if the provided [localPath] points to the Marketplace ZIP Signer CLI tool.
                 */
                localPath.orNull
                    ?.asPath
                    ?.takeIfExists()
            },
            "$subject specified with a dependencies" to {
                /**
                 * Resolves the Marketplace ZIP Signer CLI tool with the [Configurations.MARKETPLACE_ZIP_SIGNER] configuration.
                 */
                marketplaceZipSigner.singleOrNull()
                    ?.toPath()
            },
        )
}
