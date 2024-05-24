// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import com.jetbrains.plugin.structure.base.utils.exists
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.tasks.aware.parse
import org.jetbrains.intellij.platform.gradle.utils.asPath

/**
 * Provides command line arguments for launching IntelliJ Platform locally.
 *
 * @property pluginXml The plugin.xml file
 */
class PluginArgumentProvider(
    @InputFile
    @PathSensitive(RELATIVE)
    @Optional
    val pluginXml: Provider<RegularFile>,
) : CommandLineArgumentProvider {

    /**
     * Defines the plugin required to be present when IDE is started.
     */
    private val requiredPlugins
        get() = pluginXml.orNull
            ?.takeIf { it.asPath.exists() }
            ?.let { "-Didea.required.plugins.id=${it.parse { id }}" }

    /**
     * Combines various arguments related to the IntelliJ Platform configuration to create a list of arguments to be passed to the platform.
     *
     * @return The list of arguments to be passed to the platform.
     */
    override fun asArguments() = listOfNotNull(requiredPlugins)
}
