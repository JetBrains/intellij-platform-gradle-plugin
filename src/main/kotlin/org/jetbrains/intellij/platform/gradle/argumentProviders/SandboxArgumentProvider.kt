// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.io.File
import kotlin.io.path.listDirectoryEntries

class SandboxArgumentProvider(
    @InputDirectory @PathSensitive(RELATIVE) val sandboxConfigDirectory: Provider<Directory>,
    @InputDirectory @PathSensitive(RELATIVE) val sandboxPluginsDirectory: Provider<Directory>,
    @OutputDirectory val sandboxSystemDirectory: Provider<Directory>,
    @OutputDirectory val sandboxLogDirectory: Provider<Directory>,
) : CommandLineArgumentProvider {

    private val pluginPath
        get() = sandboxPluginsDirectory.asPath.listDirectoryEntries().joinToString("${File.pathSeparator},")

    override fun asArguments() = listOf(
        "-Didea.config.path=${sandboxConfigDirectory.asPath}",
        "-Didea.system.path=${sandboxSystemDirectory.asPath}",
        "-Didea.log.path=${sandboxLogDirectory.asPath}",
        "-Didea.plugins.path=${sandboxPluginsDirectory.asPath}",
        "-Dplugin.path=$pluginPath", // TODO: redundant?
    )
}
