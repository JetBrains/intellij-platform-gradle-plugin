// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test

class BuildFeaturesIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "build-features",
) {

    private val defaultArgs = listOf("--info")

    private val defaultProjectProperties = mapOf(
        "intellijPlatform.version" to intellijPlatformVersion,
        "intellijPlatform.type" to intellijPlatformType,
        "buildSearchableOptions" to false,
    )

    @Test
    fun `selfUpdateCheck is disabled`() {
        val flag = BuildFeature.SELF_UPDATE_CHECK.toString()
        println("flag = ${flag}")

        build(
            "assemble",
            projectProperties = defaultProjectProperties + mapOf(flag to false),
            args = defaultArgs,
        ) {
            output containsText "Build feature is disabled: $flag"
        }
    }

    @Test
    fun `noSearchableOptionsWarning is disabled`() {
        val flag = BuildFeature.NO_SEARCHABLE_OPTIONS_WARNING.toString()

        build(
            "jarSearchableOptions",
            projectProperties = defaultProjectProperties + mapOf(flag to false, "buildSearchableOptions" to true),
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
            "jarSearchableOptions",
            projectProperties = defaultProjectProperties + mapOf(flag to true, "buildSearchableOptions" to true),
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
            "buildSearchableOptions",
            projectProperties = defaultProjectProperties + mapOf(flag to false, "buildSearchableOptions" to true),
            args = defaultArgs,
        ) {
            output containsText "Build feature is disabled: $flag"
        }
    }

    @Test
    fun `paidPluginSearchableOptionsWarning is enabled`() {
        val flag = BuildFeature.PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING.toString()

        dir.resolve("src/main/resources/META-INF/plugin.xml").also {
            it.deleteIfExists()
            it.ensureFileExists().writeText(
                """
                <idea-plugin>
                    <id>test</id>
                    <name>Test</name>
                    <vendor>JetBrains</vendor>
                    <product-descriptor code="GIJP" release-date="20220701" release-version="20221"/>
                </idea-plugin>
                """.trimIndent()
            )
        }

        build(
            "buildSearchableOptions",
            projectProperties = defaultProjectProperties + mapOf(flag to true, "buildSearchableOptions" to true),
            args = defaultArgs,
        ) {
            output containsText "Due to IDE limitations, it is impossible to run the IDE in headless mode to collect searchable options for a paid plugin."
        }
    }
}
