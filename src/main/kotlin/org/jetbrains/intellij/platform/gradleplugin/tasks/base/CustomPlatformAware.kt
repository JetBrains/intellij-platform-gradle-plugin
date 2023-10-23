// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks.base

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType
import java.io.File

interface CustomPlatformAware {

    @get:Input
    @get:Optional
    val type: Property<IntelliJPlatformType>

    @get:Input
    @get:Optional
    val version: Property<String>

    @get:Input
    @get:Optional
    val localPath: Property<File>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val intelliJPlatform: ConfigurableFileCollection

    @get:InputDirectory
    val intellijPlatformDirectory: DirectoryProperty
}
