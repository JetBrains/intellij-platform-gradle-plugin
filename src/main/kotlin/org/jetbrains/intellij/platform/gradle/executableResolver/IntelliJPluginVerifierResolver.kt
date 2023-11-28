// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.executableResolver

import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.jetbrains.intellij.platform.gradle.*
import java.nio.file.Path
import kotlin.io.path.exists

class IntelliJPluginVerifierResolver(
    val intellijPluginVerifier: FileCollection,
    val localPath: RegularFileProperty,
    val context: String? = null,
) : ExecutableResolver {

    override fun resolveExecutable(): Path {
        debug(context, "Resolving runtime directory.")

        return listOf(
            {
                localPath.orNull?.let { file ->
                    file.asPath
                        .takeIf { it.exists() }
                        .also { debug(context, "Plugin Verifier specified with a local path: $file") }
                        .ifNull { debug(context, "Cannot resolve Plugin Verifier: $file") }
                }
            },
            {
                intellijPluginVerifier.singleOrNull()?.let { file ->
                    file.toPath()
                        .also { debug(context, "Plugin Verifier specified with dependencies resolved as: $it") }
                        .ifNull { debug(context, "Cannot resolve Plugin Verifier: $file") }
                }
            },
        )
            .asSequence()
            .mapNotNull { it() }
            .firstOrNull()
            ?.also { info(context, "Resolved IntelliJ Plugin Verifier: $it") }
            .throwIfNull { Exception("No Plugin Verifier executable found") } // TODO: suggest adding missing dependency
    }

    override fun resolveDirectory() = resolveExecutable().parent
}
