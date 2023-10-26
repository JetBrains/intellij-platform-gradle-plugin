// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks.base

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

interface JetBrainsRuntimeAware : PlatformVersionAware {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val jetbrainsRuntime: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val jetbrainsRuntimeDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val jetbrainsRuntimeExecutable: RegularFileProperty
}
