// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks

import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginSpecBase
import kotlin.test.Test

@Suppress("ComplexRedundantLet")
class SetupDependenciesTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `idea dependency is available`() {
        val testTaskName = "${Tasks.SETUP_DEPENDENCIES}Test"

        buildFile.groovy(
            """
            def classes = providers.provider {
                tasks.named('${Tasks.SETUP_DEPENDENCIES}').get().idea.get().classes
            }
            tasks.register('$testTaskName') {
                doLast {
                    println classes.get()
                }
                
                dependsOn('${Tasks.SETUP_DEPENDENCIES}')
            }
            
            """.trimIndent()
        )

        build(testTaskName).let {
            assertContains("> Task :$testTaskName", it.output)
            assertContains("ideaIC-2021.2.4", it.output)
        }
        build(testTaskName).let {
            assertContains("Reusing configuration cache.", it.output)
        }
    }
}
