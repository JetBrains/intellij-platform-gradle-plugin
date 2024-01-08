// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.base

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

interface RuntimeAware : PlatformVersionAware {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val runtimeDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val runtimeExecutable: RegularFileProperty

    @get:Internal
    val runtimeArch: Property<String>
}
