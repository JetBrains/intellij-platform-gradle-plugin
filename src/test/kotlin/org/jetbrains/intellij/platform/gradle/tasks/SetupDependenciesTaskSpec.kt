// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.test.Test

class SetupDependenciesTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `throw error when called`() {
        build(Tasks.SETUP_DEPENDENCIES) {
            assertContains("Task is scheduled for removal, see: [SDK Docs link]", output)
        }
    }
}
