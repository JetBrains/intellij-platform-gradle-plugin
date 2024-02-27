// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.ifNull
import org.jetbrains.intellij.platform.gradle.utils.throwIfNull
import kotlin.io.path.exists

/**
 * Resolves Marketplace ZIP Signer.
 *
 * @param marketplaceZipSigner The [Configurations.MARKETPLACE_ZIP_SIGNER] configuration.
 * @param localPath The local path to the Marketplace ZIP Signer file.
 */
class MarketplaceZipSignerResolver(
    val marketplaceZipSigner: FileCollection,
    val localPath: RegularFileProperty,
) : PathResolver {

    private val log = Logger(javaClass)

    /**
     * Resolves Marketplace ZIP Signer with:
     * - a direct path passed with [localPath]
     * - a dependency added to the [Configurations.MARKETPLACE_ZIP_SIGNER] configuration
     *
     * @return Marketplace ZIP Signer executable path
     * @throws GradleException if no executable found
     */
    override fun resolve() = listOf(
        {
            localPath.orNull?.let { file ->
                file.asPath
                    .takeIf { it.exists() }
                    .also { log.debug("Marketplace ZIP Signer specified with a local path: $file") }
                    .ifNull { log.debug("Cannot resolve Marketplace ZIP Signer: $file") }
            }
        },
        {
            marketplaceZipSigner.singleOrNull()?.toPath().let { file ->
                file
                    .also { log.debug("Marketplace ZIP Signer specified with dependencies resolved as: $file") }
                    .ifNull { log.debug("Cannot resolve Marketplace ZIP Signer: $file") }
            }
        },
    )
        .also { log.debug("Resolving Marketplace ZIP Signer tool.") }
        .asSequence()
        .mapNotNull { it() }
        .firstOrNull()
        ?.also { log.info("Resolved Marketplace ZIP Signer: $it") }
        .throwIfNull { GradleException("No Marketplace ZIP Signer executable found") } // TODO: suggest adding missing dependency
}
