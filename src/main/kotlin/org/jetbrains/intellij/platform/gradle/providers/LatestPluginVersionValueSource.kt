// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.resolveLatestVersion

/**
 * Obtains the latest IntelliJ Platform Gradle Plugin version.
 */
abstract class LatestPluginVersionValueSource : ValueSource<String, ValueSourceParameters.None> {

    private val coordinates = Coordinates("org.jetbrains.intellij.platform", "intellij-platform-gradle-plugin")
    private val repositoryUrl = Locations.MAVEN_GRADLE_PLUGIN_PORTAL_REPOSITORY

    override fun obtain() = coordinates.resolveLatestVersion(repositoryUrl)
}
