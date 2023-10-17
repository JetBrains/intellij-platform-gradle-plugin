// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.propertyProviders

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradleplugin.model.productInfo
import org.jetbrains.intellij.platform.gradleplugin.resolveIdeHomeVariable
import java.nio.file.Path

class LaunchSystemArgumentProvider(
    @InputDirectory @PathSensitive(RELATIVE) val ideDirectory: Path,
    @InputDirectory @PathSensitive(RELATIVE) val configDirectory: DirectoryProperty,
    @InputDirectory @PathSensitive(RELATIVE) val systemDirectory: DirectoryProperty,
    @InputDirectory @PathSensitive(RELATIVE) val pluginsDirectory: DirectoryProperty,
    private val requirePluginIds: List<String>,
) : CommandLineArgumentProvider {

    private val currentLaunchProperties
        get() = ideDirectory
            .productInfo()
            .currentLaunch
            .additionalJvmArguments
            .filter { it.startsWith("-D") }
            .map { it.resolveIdeHomeVariable(ideDirectory) }

    override fun asArguments() = currentLaunchProperties + listOf(
        "-Didea.config.path=${configDirectory.asFile.get().absolutePath}",
        "-Didea.system.path=${systemDirectory.asFile.get().absolutePath}",
        "-Didea.log.path=${systemDirectory.asFile.get().resolve("log").absolutePath}",
        "-Didea.plugins.path=${pluginsDirectory.asFile.get().absolutePath}",
        "-Didea.required.plugins.id=${requirePluginIds.joinToString(",")}",
    )
}
