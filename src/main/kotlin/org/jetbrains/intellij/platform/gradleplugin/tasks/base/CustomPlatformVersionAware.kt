// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks.base

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType

interface CustomPlatformVersionAware : PlatformVersionAware {

    @get:Input
    @get:Optional
    val type: Property<IntelliJPlatformType>

    @get:Input
    @get:Optional
    val version: Property<String>

    @get:InputDirectory
    @get:Optional
    val localPath: DirectoryProperty
}
