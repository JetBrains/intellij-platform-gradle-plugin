package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.provider.Property
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
