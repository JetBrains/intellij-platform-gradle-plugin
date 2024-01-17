// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.executableResolver

import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.ifNull
import org.jetbrains.intellij.platform.gradle.utils.throwIfNull
import java.nio.file.Path
import kotlin.io.path.exists

class IntelliJPluginVerifierResolver(
    val intellijPluginVerifier: FileCollection,
    val localPath: RegularFileProperty,
    val context: String? = null,
) : ExecutableResolver {

    private val log = Logger(javaClass)
    
    override fun resolveExecutable(): Path {
        log.debug("Resolving runtime directory.")

        return listOf(
            {
                localPath.orNull?.let { file ->
                    file.asPath
                        .takeIf { it.exists() }
                        .also { log.debug("Plugin Verifier specified with a local path: $file") }
                        .ifNull { log.debug("Cannot resolve Plugin Verifier: $file") }
                }
            },
            {
                intellijPluginVerifier.singleOrNull()?.let { file ->
                    file.toPath()
                        .also { log.debug("Plugin Verifier specified with dependencies resolved as: $it") }
                        .ifNull { log.debug("Cannot resolve Plugin Verifier: $file") }
                }
            },
        )
            .asSequence()
            .mapNotNull { it() }
            .firstOrNull()
            ?.also { log.info("Resolved IntelliJ Plugin Verifier: $it") }
            .throwIfNull { Exception("No Plugin Verifier executable found") } // TODO: suggest adding missing dependency
    }

    override fun resolveDirectory() = resolveExecutable().parent
}
