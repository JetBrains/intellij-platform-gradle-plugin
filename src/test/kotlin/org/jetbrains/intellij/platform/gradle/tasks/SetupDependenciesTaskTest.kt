// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import kotlin.test.Test

class SetupDependenciesTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `throw error when called`() {
        build(Tasks.SETUP_DEPENDENCIES) {
            assertContains("The setupDependencies task is scheduled for removal, see: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-migration.html#setupdependencies", output)
        }
    }
}