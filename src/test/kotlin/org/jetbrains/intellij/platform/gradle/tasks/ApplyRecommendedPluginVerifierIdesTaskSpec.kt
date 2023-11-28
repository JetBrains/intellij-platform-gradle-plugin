// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase

class ApplyRecommendedPluginVerifierIdesTaskSpec : IntelliJPluginSpecBase() {

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
            assertContains("Starting the IntelliJ Plugin Verifier 1.255", output)
        }
    }

    fun `run task if recommended IDEs requested`() {
        buildFile.kotlin(
            """
            intellijPlatform {
                pluginVerifier {
                    recommended()
                }
            }
            """.trimIndent()
        )

        build(Tasks.APPLY_RECOMMENDED_PLUGIN_VERIFIER_IDES) {
            assertContains("Starting the IntelliJ Plugin Verifier 1.255", output)
        }
    }
}
