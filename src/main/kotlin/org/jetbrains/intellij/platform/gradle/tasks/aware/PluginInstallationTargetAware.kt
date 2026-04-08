package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

interface PluginInstallationTargetAware {

    /**
     * Specifies in which part of the product the developed plugin should be installed.
     *
     * Effective default value: [SplitModeAware.PluginInstallationTarget.BACKEND]
     */
    @get:Input
    @get:Optional
    val pluginInstallationTarget: Property<SplitModeAware.PluginInstallationTarget>
}

internal fun Provider<SplitModeAware.PluginInstallationTarget>.asSplitModeTarget() =
    map { it.toSplitModeTarget() }

internal fun Provider<SplitModeAware.SplitModeTarget>.asPluginInstallationTarget() =
    map { it.toPluginInstallationTarget() }

internal fun Property<SplitModeAware.SplitModeTarget>.conventionFrom(
    pluginInstallationTarget: Provider<SplitModeAware.PluginInstallationTarget>,
) {
    convention(pluginInstallationTarget.asSplitModeTarget().orElse(SplitModeAware.SplitModeTarget.BACKEND))
}

internal fun Property<SplitModeAware.SplitModeTarget>.conventionFrom(
    pluginInstallationTarget: Provider<SplitModeAware.PluginInstallationTarget>,
    fallback: Provider<SplitModeAware.SplitModeTarget>,
) {
    convention(pluginInstallationTarget.asSplitModeTarget().orElse(fallback))
}

internal fun SplitModeAware.frontendProcessPluginsDirectory() =
    effectivePluginInstallationTarget.flatMap {
        when (it) {
            SplitModeAware.PluginInstallationTarget.BOTH -> sandboxPluginsDirectory
            else -> sandboxPluginsFrontendDirectory
        }
    }

internal fun SplitModeAware.pluginInstallationDirectory(): Provider<Directory> =
    splitMode.zip(effectivePluginInstallationTarget) { isSplitMode, target ->
        when {
            !isSplitMode -> sandboxPluginsDirectory.get()
            target == SplitModeAware.PluginInstallationTarget.FRONTEND -> sandboxPluginsFrontendDirectory.get()
            else -> sandboxPluginsDirectory.get()
        }
    }
