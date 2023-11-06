// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.base

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

interface SandboxAware : PlatformVersionAware {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val sandboxDirectory: DirectoryProperty
}
