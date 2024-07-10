// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

private const val ASSEMBLE = "assemble"

class GradlePropertiesIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "gradle-properties",
) {

    private val defaultArgs = listOf("--info")

    override val defaultProjectProperties
        get() = super.defaultProjectProperties + mapOf(
            "buildSearchableOptions" to true,
        )

    @Test
    fun `selfUpdateCheck is disabled`() {
        val property = GradleProperties.SelfUpdateCheck.toString()

        build(
            ASSEMBLE,
            projectProperties = defaultProjectProperties + mapOf(
                property to false,
                "buildSearchableOptions" to false,
            ),
            args = defaultArgs,
        ) {
            output containsText "Read Gradle property: $property=false"
        }
    }

    @Test
    fun `noSearchableOptionsWarning is disabled`() {
        val property = GradleProperties.NoSearchableOptionsWarning.toString()

        build(
            Tasks.JAR_SEARCHABLE_OPTIONS,
            projectProperties = defaultProjectProperties + mapOf(property to false),
            args = defaultArgs,
        ) {
            output containsText "Read Gradle property: $property=false"
            output notContainsText "No searchable options found."
        }
    }

    @Test
    fun `noSearchableOptionsWarning is enabled`() {
        val property = GradleProperties.NoSearchableOptionsWarning.toString()

        build(
            Tasks.JAR_SEARCHABLE_OPTIONS,
            projectProperties = defaultProjectProperties + mapOf(property to true),
            args = defaultArgs,
        ) {
            output containsText "Read Gradle property: $property=true"
            output containsText "No searchable options found."
        }
    }

    @Test
    fun `paidPluginSearchableOptionsWarning is disabled`() {
        val property = GradleProperties.PaidPluginSearchableOptionsWarning.toString()

        build(
            Tasks.BUILD_SEARCHABLE_OPTIONS,
            projectProperties = defaultProjectProperties + mapOf(property to false),
            args = defaultArgs,
        ) {
            output containsText "Read Gradle property: $property=false"
        }
    }

    @Test
    fun `paidPluginSearchableOptionsWarning is enabled`() {
        val property = GradleProperties.PaidPluginSearchableOptionsWarning.toString()

        build(
            Tasks.BUILD_SEARCHABLE_OPTIONS,
            projectProperties = defaultProjectProperties + mapOf(property to true),
            args = defaultArgs,
        ) {
            output containsText "Due to IDE limitations, it is impossible to run the IDE in headless mode to collect searchable options for a paid plugin."
        }
    }
}
