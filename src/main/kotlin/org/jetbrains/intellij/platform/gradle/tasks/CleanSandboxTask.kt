// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.named
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks

/**
 * Cleans sandbox data produced for the current Gradle project.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class CleanSandboxTask : Delete() {

    init {
        group = Plugin.GROUP_NAME
        description = "Cleans the sandbox directory for the current project."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<CleanSandboxTask>(Tasks.CLEAN_SANDBOX) {
                val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)

                delete(prepareSandboxTaskProvider.flatMap { it.sandboxDirectory })
            }
    }
}
