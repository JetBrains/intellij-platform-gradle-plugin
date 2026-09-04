// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.newInstance

/**
 * Configures a dependency on a non-bundled IntelliJ Platform plugin.
 *
 * Exclusions apply within the plugin's `lib/` and `lib/modules/` directories on both project classpaths and sandbox
 * installations. Patterns declared for the same plugin ID are combined build-wide.
 */
abstract class IntelliJPlatformPluginConfiguration {

    /**
     * Excludes files bundled with the plugin whose paths match [pattern].
     *
     * The pattern uses Gradle's Ant-style matching relative to the plugin's `lib/` and `lib/modules/` directories,
     * for example `jena-*.jar`.
     */
    fun excludeBundledLibrary(pattern: String) {
        excludedBundledLibraries += pattern
    }

    /**
     * Excludes files bundled with the plugin whose paths match any of [patterns].
     */
    fun excludeBundledLibraries(vararg patterns: String) {
        excludedBundledLibraries.addAll(patterns)
    }

    internal val excludedBundledLibraries = mutableSetOf<String>()
}

internal fun ObjectFactory.collectExcludedBundledLibraries(configure: Action<IntelliJPlatformPluginConfiguration>) =
    newInstance<IntelliJPlatformPluginConfiguration>()
        .also(configure::execute)
        .excludedBundledLibraries
        .toSet()
