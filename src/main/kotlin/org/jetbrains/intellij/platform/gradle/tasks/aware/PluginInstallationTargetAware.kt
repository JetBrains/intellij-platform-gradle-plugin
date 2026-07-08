package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

interface PluginInstallationTargetAware : IntelliJPlatformAware {

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

internal fun Provider<SplitModeAware.SplitModeTarget>.asExplicitPluginInstallationTarget() =
    asPluginInstallationTarget().filter { it != SplitModeAware.PluginInstallationTarget.BACKEND }

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

private const val FRONTEND_PLATFORM_MODULE_PREFIX = "intellij.platform.frontend"
private const val BACKEND_PLATFORM_MODULE_SUFFIX = ".backend"

internal fun Iterable<String>.inferredPluginInstallationTarget() =
    map(String::trim)
        .filter(String::isNotEmpty)
        .fold(null as SplitModeAware.PluginInstallationTarget?) { target, module ->
            val moduleTarget = when {
                module == FRONTEND_PLATFORM_MODULE_PREFIX || module.startsWith("$FRONTEND_PLATFORM_MODULE_PREFIX.") ->
                    SplitModeAware.PluginInstallationTarget.FRONTEND

                module.endsWith(BACKEND_PLATFORM_MODULE_SUFFIX) ->
                    SplitModeAware.PluginInstallationTarget.BACKEND

                else -> null
            }
            target.combineWith(moduleTarget)
        }

private fun SplitModeAware.PluginInstallationTarget?.combineWith(other: SplitModeAware.PluginInstallationTarget?) = when {
    this == null -> other
    other == null -> this
    this == other -> this
    else -> SplitModeAware.PluginInstallationTarget.BOTH
}
