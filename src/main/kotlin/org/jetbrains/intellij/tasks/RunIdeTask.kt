// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.tasks.UntrackedTask

@UntrackedTask(because = "Should always run guest IDE")
abstract class RunIdeTask : RunIdeBase()
