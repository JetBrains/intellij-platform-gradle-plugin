// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

/**
 * Provides command line arguments for working in a sandbox environment.
 *
 * @property sandboxConfigDirectory The input directory containing the sandbox configuration files.
 * @property sandboxPluginsDirectory The input directory containing the sandbox plugins.
 * @property sandboxSystemDirectory The output directory where the sandbox system files will be written.
 * @property sandboxLogDirectory The output directory where the sandbox log files will be written.
 */
class SandboxArgumentProvider(
    @Optional
    @InputDirectory
    @PathSensitive(RELATIVE)
    val sandboxConfigDirectory: DirectoryProperty,

    @Optional
    @InputDirectory
    @PathSensitive(RELATIVE)
    val sandboxPluginsDirectory: DirectoryProperty,

    @Internal
    val sandboxSystemDirectory: DirectoryProperty,

    @Internal
    val sandboxLogDirectory: DirectoryProperty,
) : CommandLineArgumentProvider {

    /**
     * The path to the directory containing the sandbox plugins.
     *
     * @TODO: redundant?
     */
    private val pluginPath
        get() = sandboxPluginsDirectory.ifExists {
            it.listDirectoryEntries().joinToString("${File.pathSeparator},")
        }

    private fun computePluginPathProperties() = listOfNotNull(
        sandboxPluginsDirectory.ifExists { "-Didea.plugins.path=$it" },
        pluginPath?.let { "-Dplugin.path=$it" },
    )

    override fun asArguments() = listOfNotNull(
        sandboxConfigDirectory.ifExists { "-Didea.config.path=$it" },
        sandboxSystemDirectory.ifExists { "-Didea.system.path=$it" },
        sandboxLogDirectory.ifExists { "-Didea.log.path=$it" }
    ) + computePluginPathProperties()

    private fun <T> DirectoryProperty.ifExists(block: (Path) -> T) = orNull?.asPath?.takeIf { it.exists() }?.run(block)
}
