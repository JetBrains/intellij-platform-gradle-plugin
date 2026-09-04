// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.provider.ListProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Input
import java.io.Serializable

internal data class PluginLibraryExclusion(
    val pluginId: String,
    val pattern: String,
) : Serializable

/**
 * Retains bundled-library exclusion declarations contributed by every project in the build.
 *
 * The declarations live in service parameters, so Gradle can restore them with the configuration cache.
 */
internal abstract class PluginLibraryExclusionsService : BuildService<PluginLibraryExclusionsService.Parameters> {

    interface Parameters : BuildServiceParameters {

        @get:Input
        val entries: ListProperty<PluginLibraryExclusion>
    }
}
