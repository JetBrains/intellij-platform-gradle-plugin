// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

class VerifyPluginConfigurationIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "verify-plugin-configuration",
) {

    private val issuesFoundSentence = "[gradle-intellij-plugin :verifyPluginConfiguration] The following plugin configuration issues were found:"

    private val userHome = dir.resolve("home")
    private val defaultSystemProperties = mapOf(
        "user.home" to userHome,
    )
    private val defaultProjectProperties = mapOf(
        "intellijVersion" to "2022.3",
        "sinceBuild" to "223",
        "languageVersion" to "17",
        "downloadDirectory" to dir.resolve("home"),
    )

    @Test
    fun `should not report issues on valid configuration`() {
        build(
            Tasks.External.CLEAN,
            Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION,
            systemProperties = defaultSystemProperties,
            projectProperties = defaultProjectProperties,
        ) {
            output notContainsText issuesFoundSentence
        }
    }

    @Test
    fun `should report incorrect source compatibility`() {
        build(
            Tasks.External.CLEAN,
            Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION,
            systemProperties = defaultSystemProperties,
            projectProperties = defaultProjectProperties + mapOf("languageVersion" to "11"),
        ) {
            output containsText issuesFoundSentence
            output containsText "- The Java configuration specifies sourceCompatibility=11 but IntelliJ Platform 2022.3 requires sourceCompatibility=17."
        }
    }

    @Test
    fun `should report incorrect target compatibility`() {
        build(
            Tasks.External.CLEAN,
            Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION,
            systemProperties = defaultSystemProperties,
            projectProperties = defaultProjectProperties + mapOf("sinceBuild" to "203"),
        ) {
            output containsText issuesFoundSentence
            output containsText "- The 'since-build' property is lower than the target IntelliJ Platform major version: 203 < 223."
            output containsText "- The Java configuration specifies targetCompatibility=17 but since-build='203' property requires targetCompatibility=11."
        }
    }
}
