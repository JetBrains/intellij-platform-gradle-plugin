// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.executableResolver

import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.ifNull
import kotlin.io.path.exists

class MarketplaceZipSignerResolver(
    val marketplaceZipSigner: FileCollection,
    val localPath: RegularFileProperty,
    val context: String? = null,
) : ExecutableResolver {

    private val log = Logger(javaClass)

    override fun resolveExecutable() = listOf(
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
        .asSequence()
        .mapNotNull { it() }
        .firstOrNull()
        ?.also { log.info("Resolved Marketplace ZIP Signer: $it") }

    override fun resolveDirectory() = resolveExecutable()?.parent
}
