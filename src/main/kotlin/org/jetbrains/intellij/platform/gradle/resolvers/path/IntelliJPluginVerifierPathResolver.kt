// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.utils.asPath

/**
 * Resolves the path to the IntelliJ Plugin Verifier executable.
 *
 * @property intellijPluginVerifier The [Configurations.INTELLIJ_PLUGIN_VERIFIER] configuration.
 * @property localPath The local path to the IntelliJ Plugin Verifier file.
 */
class IntelliJPluginVerifierPathResolver(
    private val intellijPluginVerifier: FileCollection,
    private val localPath: Provider<RegularFile>,
) : PathResolver() {

    override val subject = "IntelliJ Plugin Verifier"

    override val subjectInput
        get() = "intellijPluginVerifier[${intellijPluginVerifier.joinToString(":")}]," +
                "localPath[${localPath.orNull?.asPath}]"

    override val predictions = sequenceOf(
        "$subject specified with a local path" to {
            /**
             * Checks if the provided [localPath] points to the IntelliJ Plugin Verifier CLI tool.
             */
            localPath.orNull
                ?.asPath
                ?.takeIfExists()
        },
        "$subject specified with dependencies" to {
            /**
             * Resolves the IntelliJ Plugin Verifier CLI tool with the [Configurations.INTELLIJ_PLUGIN_VERIFIER] configuration.
             */
            intellijPluginVerifier.singleOrNull()
                ?.toPath()
        },
    )
}
