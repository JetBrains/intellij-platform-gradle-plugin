// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

class BuildFeaturesIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "build-features",
) {

    private val defaultArgs = listOf("--info")

    override val defaultProjectProperties
        get() = super.defaultProjectProperties + mapOf(
            "buildSearchableOptions" to true,
        )

    @Test
    fun `selfUpdateCheck is disabled`() {
        val flag = BuildFeature.SELF_UPDATE_CHECK.toString()

        build(
            Tasks.External.ASSEMBLE,
            projectProperties = defaultProjectProperties + mapOf(flag to false, "buildSearchableOptions" to false),
            args = defaultArgs,
        ) {
            output containsText "Build feature is disabled: $flag"
        }
    }

    @Test
    fun `noSearchableOptionsWarning is disabled`() {
        val flag = BuildFeature.NO_SEARCHABLE_OPTIONS_WARNING.toString()

        build(
            Tasks.JAR_SEARCHABLE_OPTIONS,
            projectProperties = defaultProjectProperties + mapOf(flag to false),
            args = defaultArgs,
        ) {
            output containsText "Build feature is disabled: $flag"
            output notContainsText "No searchable options found."
        }
    }

    @Test
    fun `noSearchableOptionsWarning is enabled`() {
        val flag = BuildFeature.NO_SEARCHABLE_OPTIONS_WARNING.toString()

        build(
            Tasks.JAR_SEARCHABLE_OPTIONS,
            projectProperties = defaultProjectProperties + mapOf(flag to true),
            args = defaultArgs,
        ) {
            output containsText "Build feature is enabled: $flag"
            output containsText "No searchable options found."
        }
    }

    @Test
    fun `paidPluginSearchableOptionsWarning is disabled`() {
        val flag = BuildFeature.PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING.toString()

        build(
            Tasks.BUILD_SEARCHABLE_OPTIONS,
            projectProperties = defaultProjectProperties + mapOf(flag to false),
            args = defaultArgs,
        ) {
            output containsText "Build feature is disabled: $flag"
        }
    }

    @Test
    fun `paidPluginSearchableOptionsWarning is enabled`() {
        val flag = BuildFeature.PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING.toString()

        build(
            Tasks.BUILD_SEARCHABLE_OPTIONS,
            projectProperties = defaultProjectProperties + mapOf(flag to true),
            args = defaultArgs,
        ) {
            output containsText "Due to IDE limitations, it is impossible to run the IDE in headless mode to collect searchable options for a paid plugin."
        }
    }
}
