// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.utils.platformPath

internal class IdeaHomePathArgumentProvider(
    @InputFiles
    @PathSensitive(RELATIVE)
    val intellijPlatformConfiguration: FileCollection,
) : CommandLineArgumentProvider {

    override fun asArguments() = listOf("-Didea.home.path=${intellijPlatformConfiguration.platformPath()}")
}
