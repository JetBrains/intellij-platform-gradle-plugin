// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.propertyProviders

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.asPath
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.resolveIdeHomeVariable

class LaunchSystemArgumentProvider(
    @InputFiles @PathSensitive(RELATIVE) val intellijPlatform: ConfigurableFileCollection,
    @InputDirectory @PathSensitive(RELATIVE) val sandboxConfigDirectory: Provider<Directory>,
    @InputDirectory @PathSensitive(RELATIVE) val sandboxPluginsDirectory: Provider<Directory>,
    @InputDirectory @PathSensitive(RELATIVE) val sandboxSystemDirectory: Provider<Directory>,
    @InputDirectory @PathSensitive(RELATIVE) val sandboxLogDirectory: Provider<Directory>,
    private val requirePluginIds: List<String>,
) : CommandLineArgumentProvider {

    private val intellijPlatformPath
        get() = intellijPlatform.singleFile.toPath()

    private val currentLaunchProperties
        get() = intellijPlatformPath
            .productInfo()
            .currentLaunch
            .additionalJvmArguments
            .filter { it.startsWith("-D") }
            .map { it.resolveIdeHomeVariable(intellijPlatformPath) }

    override fun asArguments() = currentLaunchProperties + listOf(
        "-Didea.config.path=${sandboxConfigDirectory.asPath}",
        "-Didea.system.path=${sandboxSystemDirectory.asPath}",
        "-Didea.log.path=${sandboxLogDirectory.asPath}",
        "-Didea.plugins.path=${sandboxPluginsDirectory.asPath}",
        "-Didea.required.plugins.id=${requirePluginIds.joinToString(",")}",
    )
}
