// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.propertyProviders

import com.jetbrains.plugin.structure.base.utils.listFiles
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Sandbox
import org.jetbrains.intellij.platform.gradle.asPath
import java.io.File

class PluginPathArgumentProvider(
    @InputDirectory @PathSensitive(RELATIVE) val sandboxDirectory: DirectoryProperty,
) : CommandLineArgumentProvider {

    private val paths
        get() = sandboxDirectory.map {
            it.dir(Sandbox.PLUGINS).apply {
                asFile.mkdirs()
            }
        }.asPath.listFiles().joinToString("${File.pathSeparator},")

    override fun asArguments() = listOf(
        "-Dplugin.path=$paths",
    )
}
