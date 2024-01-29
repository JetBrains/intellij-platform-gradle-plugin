// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.io.File
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
    @InputDirectory @PathSensitive(RELATIVE) val sandboxConfigDirectory: DirectoryProperty,
    @InputDirectory @PathSensitive(RELATIVE) val sandboxPluginsDirectory: DirectoryProperty,
    @OutputDirectory val sandboxSystemDirectory: DirectoryProperty,
    @OutputDirectory val sandboxLogDirectory: DirectoryProperty,
) : CommandLineArgumentProvider {

    /**
     * The path to the directory containing the sandbox plugins.
     *
     * @TODO: redundant?
     */
    private val pluginPath
        get() = sandboxPluginsDirectory.asPath.listDirectoryEntries().joinToString("${File.pathSeparator},")

    override fun asArguments() = listOf(
        "-Didea.config.path=${sandboxConfigDirectory.asPath}",
        "-Didea.system.path=${sandboxSystemDirectory.asPath}",
        "-Didea.log.path=${sandboxLogDirectory.asPath}",
        "-Didea.plugins.path=${sandboxPluginsDirectory.asPath}",
        "-Dplugin.path=$pluginPath",
    )
}
