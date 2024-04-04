// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.compaion

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.tasks.TestIdeTask
import org.jetbrains.intellij.platform.gradle.tasks.registerTask

class TestCompanion {
    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<Test>(Tasks.External.TEST, configureWithType = false, configuration = TestIdeTask.configuration)
    }
}
