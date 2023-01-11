// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_GROUP_NAME

@UntrackedTask(because = "Should always run guest IDE")
abstract class RunIdeTask : RunIdeBase() {

    init {
        group = PLUGIN_GROUP_NAME
        description = "Runs the IDE instance with the developed plugin installed."
    }
}
