// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.utils.asPath

/**
 * Resolves Marketplace ZIP Signer.
 *
 * @param marketplaceZipSigner The [Configurations.MARKETPLACE_ZIP_SIGNER] configuration.
 * @param localPath The local path to the Marketplace ZIP Signer file.
 */
class MarketplaceZipSignerPathResolver(
    private val marketplaceZipSigner: FileCollection,
    private val localPath: Provider<RegularFile>,
) : PathResolver() {

    override val subject = "Marketplace ZIP Signer"

    override val subjectInput
        get() = "localPath[${localPath.orNull?.asPath}]," +
                "marketplaceZipSigner[${marketplaceZipSigner.joinToString(":")}]"

    override val predictions = sequenceOf(
        "$subject specified with a local path" to {
            /**
             * Checks if the provided [localPath] points to the Marketplace ZIP Signer CLI tool.
             */
            localPath.orNull
                ?.asPath
                ?.takeIfExists()
        },
        "$subject specified with dependencies" to {
            /**
             * Resolves the Marketplace ZIP Signer CLI tool with the [Configurations.MARKETPLACE_ZIP_SIGNER] configuration.
             */
            marketplaceZipSigner.singleOrNull()
                ?.toPath()
        },
    )
}
