// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.resolveLatestVersion

/**
 * Obtains the latest IntelliJ Platform Gradle Plugin version.
 */
abstract class LatestPluginVersionValueSource : ValueSource<String, LatestPluginVersionValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        /**
         * Whether Gradle runs in offline mode. Captured at configuration time from
         * [org.gradle.StartParameter.isOffline] so this [ValueSource] never reads `gradle.startParameter` itself.
         */
        @get:Input
        val offline: Property<Boolean>
    }

    private val coordinates = Coordinates("org.jetbrains.intellij.platform", "intellij-platform-gradle-plugin")
    private val repositoryUrl = Locations.MAVEN_GRADLE_PLUGIN_PORTAL_REPOSITORY

    override fun obtain() = coordinates.resolveLatestVersion(repositoryUrl, parameters.offline.getOrElse(false))
}
