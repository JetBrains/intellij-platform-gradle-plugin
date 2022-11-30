// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginSpecBase
import kotlin.test.Test

@Suppress("ComplexRedundantLet")
class SetupDependenciesTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `idea dependency is available`() {
        val testTaskName = "${SETUP_DEPENDENCIES_TASK_NAME}Test"

        buildFile.groovy(
            """
            def classes = providers.provider {
                tasks.named('$SETUP_DEPENDENCIES_TASK_NAME').get().idea.get().classes
            }
            tasks.register('$testTaskName') {
                doLast {
                    println classes.get()
                }
                
                dependsOn('$SETUP_DEPENDENCIES_TASK_NAME')
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

    @Test
    fun `reuse configuration cache`() {
        build(SETUP_DEPENDENCIES_TASK_NAME)
        build(SETUP_DEPENDENCIES_TASK_NAME).let {
            assertContains("Reusing configuration cache.", it.output)
        }
    }
}
