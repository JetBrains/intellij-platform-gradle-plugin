// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.propertyProviders

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Sandbox
import org.jetbrains.intellij.platform.gradleplugin.asPath
import org.jetbrains.intellij.platform.gradleplugin.model.productInfo
import org.jetbrains.intellij.platform.gradleplugin.resolveIdeHomeVariable
import kotlin.io.path.pathString

class LaunchSystemArgumentProvider(
    @InputDirectory @PathSensitive(RELATIVE) val intellijPlatformDirectory: DirectoryProperty,
    @InputDirectory @PathSensitive(RELATIVE) val sandboxDirectory: DirectoryProperty,
    private val requirePluginIds: List<String>,
) : CommandLineArgumentProvider {

    private val intellijPlatformPath
        get() = intellijPlatformDirectory.asPath

    private val currentLaunchProperties
        get() = intellijPlatformPath
            .productInfo()
            .currentLaunch
            .additionalJvmArguments
            .filter { it.startsWith("-D") }
            .map { it.resolveIdeHomeVariable(intellijPlatformPath) }

    private fun resolveInSandboxDirectory(directoryName: String) = sandboxDirectory.map {
        it.dir(directoryName).apply {
            asFile.mkdirs()
        }
    }.asPath.pathString

    override fun asArguments() = currentLaunchProperties + listOf(
        "-Didea.config.path=${resolveInSandboxDirectory(Sandbox.CONFIG)}",
        "-Didea.system.path=${resolveInSandboxDirectory(Sandbox.SYSTEM)}",
        "-Didea.log.path=${resolveInSandboxDirectory(Sandbox.LOG)}",
        "-Didea.plugins.path=${resolveInSandboxDirectory(Sandbox.PLUGINS)}",
        "-Didea.required.plugins.id=${requirePluginIds.joinToString(",")}",
    )
}
