// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.propertyProviders

import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

class PluginPathArgumentProvider(
    @InputDirectory @PathSensitive(RELATIVE) val pluginsDirectory: File,
) : CommandLineArgumentProvider {

    private val paths
        get() = pluginsDirectory.listFiles()?.joinToString("${File.pathSeparator},") { it.absolutePath }.orEmpty()

    override fun asArguments() = listOf(
        "-Dplugin.path=$paths",
    )
}
