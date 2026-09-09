// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.provider.ListProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Input

/**
 * Accumulates the plugin IDs of plugin dependencies declared in the build script — added with
 * `intellijPlatform.plugin(...)`, `intellijPlatform.compatiblePlugin(...)`, and
 * `intellijPlatform.bundledPlugin(...)` — so that
 * [org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginProjectConfigurationTask] can verify that each of them is
 * reflected in the plugin descriptors.
 *
 * The declarations live in service parameters, so Gradle can restore them with the configuration cache.
 */
internal abstract class DeclaredPluginDependenciesService : BuildService<DeclaredPluginDependenciesService.Parameters> {

    interface Parameters : BuildServiceParameters {

        /**
         * Plugin IDs declared with `plugin(...)` or `compatiblePlugin(...)`.
         */
        @get:Input
        val pluginIds: ListProperty<String>

        /**
         * Plugin IDs declared with `bundledPlugin(...)`.
         */
        @get:Input
        val bundledPluginIds: ListProperty<String>
    }
}
