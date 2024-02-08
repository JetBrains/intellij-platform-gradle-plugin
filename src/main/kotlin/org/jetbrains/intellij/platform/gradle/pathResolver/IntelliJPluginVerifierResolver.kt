// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.pathResolver

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
 * Resolves the path to the IntelliJ Plugin Verifier executable.
 *
 * @property intellijPluginVerifier The [Configurations.INTELLIJ_PLUGIN_VERIFIER] configuration.
 * @property localPath The local path to the IntelliJ Plugin Verifier file.
 */
class IntelliJPluginVerifierResolver(
    val intellijPluginVerifier: FileCollection,
    val localPath: RegularFileProperty,
) : PathResolver {

    private val log = Logger(javaClass)

    /**
     * Resolves IntelliJ Plugin Verifier executable with:
     * - a direct path passed with [localPath]
     * - a dependency added to the [Configurations.INTELLIJ_PLUGIN_VERIFIER] configuration
     *
     * @return IntelliJ Plugin Verifier executable path
     * @throws GradleException if no executable found
     */
    override fun resolve() = listOf(
        {
            localPath.orNull?.let { file ->
                file.asPath
                    .takeIf { it.exists() }
                    .also { log.debug("IntelliJ Plugin Verifier specified with a local path: $file") }
                    .ifNull { log.debug("Cannot resolve IntelliJ Plugin Verifier: $file") }
            }
        },
        {
            intellijPluginVerifier.singleOrNull()?.let { file ->
                file.toPath()
                    .also { log.debug("IntelliJ Plugin Verifier specified with dependencies resolved as: $it") }
                    .ifNull { log.debug("Cannot resolve IntelliJ Plugin Verifier: $file") }
            }
        },
    )
        .also { log.debug("Resolving IntelliJ Plugin Verifier tool.") }
        .asSequence()
        .mapNotNull { it() }
        .firstOrNull()
        ?.also { log.info("Resolved IntelliJ Plugin Verifier: $it") }
        .throwIfNull { GradleException("No IntelliJ Plugin Verifier executable found") } // TODO: suggest adding missing dependency
}
