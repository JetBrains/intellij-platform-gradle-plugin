// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.Version
import kotlin.test.Ignore
import kotlin.test.Test

class VerifyPluginConfigurationIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "verify-plugin-configuration",
) {

    private val issuesFoundSentence = "[${Plugin.ID}] The following plugin configuration issues were found:"

    private val defaultSystemProperties
        get() = mapOf("user.home" to dir.resolve("home"))

    private val major = Version.parse(intellijPlatformBuildNumber).major

    override val defaultProjectProperties
        get() = super.defaultProjectProperties + mapOf(
            "sinceBuild" to "$major",
            "languageVersion" to "21",
        )

    @Test
    @Ignore
    fun `should not report issues on valid configuration`() {
        build(
            Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION,
            systemProperties = defaultSystemProperties,
            projectProperties = defaultProjectProperties,
        ) {
            output notContainsText issuesFoundSentence
        }
    }

    @Test
    @Ignore
    fun `should report incorrect source compatibility`() {
        build(
            Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION,
            systemProperties = defaultSystemProperties,
            projectProperties = defaultProjectProperties + mapOf("languageVersion" to "11"),
        ) {
            output containsText issuesFoundSentence
            output containsText "- The Java configuration specifies sourceCompatibility=11 but IntelliJ Platform 2022.3 requires sourceCompatibility=17."
        }
    }

    @Test
    @Ignore
    fun `should report incorrect target compatibility`() {
        build(
            Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION,
            systemProperties = defaultSystemProperties,
            projectProperties = defaultProjectProperties + mapOf("sinceBuild" to "203"),
        ) {
            output containsText issuesFoundSentence
            output containsText "- since-build is lower than target platform version The since-build='203' (major version 203) is lower than the target IntelliJ Platform major version '$major'. This means your plugin declares support for older IDE versions than you're building against. Update since-build in plugin.xml to match or exceed the target platform version: '$major'."
            output containsText "- Java targetCompatibility exceeds since-build requirements Java targetCompatibility is set to '21', but since-build='203' only requires Java '11'. This creates bytecode that may not be compatible with the minimum supported IDE version. Lower targetCompatibility to '11' or increase since-build to match the target Java version."
        }
    }
}
