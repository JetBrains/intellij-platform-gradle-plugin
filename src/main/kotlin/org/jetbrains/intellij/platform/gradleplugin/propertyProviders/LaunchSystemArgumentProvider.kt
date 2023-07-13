// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.propertyProviders

import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradleplugin.ideProductInfo
import org.jetbrains.intellij.platform.gradleplugin.resolveIdeHomeVariable
import java.io.File
import java.nio.file.Path

class LaunchSystemArgumentProvider(
    @InputDirectory @PathSensitive(RELATIVE) val ideDirectory: Path,
    @InputDirectory @PathSensitive(RELATIVE) val configDirectory: File,
    @InputDirectory @PathSensitive(RELATIVE) val systemDirectory: File,
    @InputDirectory @PathSensitive(RELATIVE) val pluginsDirectory: File,
    private val requirePluginIds: List<String>,
) : CommandLineArgumentProvider {

    private val currentLaunchProperties
        get() = ideProductInfo(ideDirectory)
            ?.currentLaunch
            ?.additionalJvmArguments
            ?.filter { it.startsWith("-D") }
            ?.map { it.resolveIdeHomeVariable(ideDirectory) }
            .orEmpty()

    override fun asArguments() = currentLaunchProperties + listOf(
        "-Didea.config.path=${configDirectory.absolutePath}",
        "-Didea.system.path=${systemDirectory.absolutePath}",
        "-Didea.log.path=${systemDirectory.resolve("log").absolutePath}",
        "-Didea.plugins.path=${pluginsDirectory.absolutePath}",
        "-Didea.required.plugins.id=${requirePluginIds.joinToString(",")}",
    )
}
