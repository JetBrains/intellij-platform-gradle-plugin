// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplyRecommendedPluginVerifierIdesTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `do not run task by default`() {
        buildFile.kotlin(
            """
            intellijPlatform {
                pluginVerifier {
                }
            }
            """.trimIndent()
        )

        build(Tasks.APPLY_RECOMMENDED_PLUGIN_VERIFIER_IDES) {
            assertEquals(TaskOutcome.SKIPPED, task(":${Tasks.APPLY_RECOMMENDED_PLUGIN_VERIFIER_IDES}")?.outcome)
        }
    }

    @Test
    fun `run task if recommended IDEs requested`() {
        buildFile.kotlin(
            """
            intellijPlatform {
                pluginVerifier {
                    ides {
                        recommended()
                    }
                }
            }
            """.trimIndent()
        )

        build(Tasks.APPLY_RECOMMENDED_PLUGIN_VERIFIER_IDES) {
            assertContains("Starting the IntelliJ Plugin Verifier 1.255", output)
        }
    }
}
