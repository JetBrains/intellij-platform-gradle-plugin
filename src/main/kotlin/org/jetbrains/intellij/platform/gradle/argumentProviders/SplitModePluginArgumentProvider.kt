// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware

/**
 * Conditionally enables the required-plugin argument for a specific split-mode side.
 */
internal class SplitModePluginArgumentProvider(
    @Nested
    val delegate: PluginArgumentProvider,

    @Input
    val pluginInstallationTarget: Provider<SplitModeAware.PluginInstallationTarget>,

    @Input
    val requiredTarget: SplitModeAware.PluginInstallationTarget,
) : CommandLineArgumentProvider {

    override fun asArguments() = when (pluginInstallationTarget.get().includes(requiredTarget)) {
        true -> delegate.asArguments()
        false -> emptyList()
    }
}
