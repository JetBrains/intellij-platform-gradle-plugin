// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.propertyProviders

import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.asPath
import java.io.File
import kotlin.io.path.listDirectoryEntries

class PluginPathArgumentProvider(
    @InputDirectory @PathSensitive(RELATIVE) val sandboxPluginsDirectory: Provider<Directory>,
) : CommandLineArgumentProvider {

    private val paths
        get() = sandboxPluginsDirectory.asPath.listDirectoryEntries().joinToString("${File.pathSeparator},")

    override fun asArguments() = listOf(
        "-Dplugin.path=$paths",
    )
}
