// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.gradle.api.file.Directory
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.jetbrains.intellij.platform.gradle.providers.JavaRuntimeMetadataValueSource
import org.jetbrains.intellij.platform.gradle.resolvers.path.resolveJavaRuntimeExecutable
import org.jetbrains.intellij.platform.gradle.tasks.aware.RunnableIdeAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware
import kotlin.io.path.pathString

/**
 * A custom implementation of [JavaLauncher] to make it possible to use custom Java Runtime, such as JBR bundled with IntelliJ Platform.
 * This custom launcher is used with [RunnableIdeAware] and [TestableAware] tasks.
 *
 * @param dir Java Runtime location
 * @param metadata Java Runtime metadata obtained with [JavaRuntimeMetadataValueSource]
 */
internal class IntelliJPlatformJavaLauncher(
    private val dir: Directory,
    private val metadata: MutableMap<String, String>,
) : JavaLauncher {
    override fun getMetadata() = object : JavaInstallationMetadata {
        override fun getLanguageVersion() = JavaLanguageVersion.of(metadata["java.specification.version"].orEmpty())

        override fun getJavaRuntimeVersion() = metadata["java.runtime.version"].orEmpty()

        override fun getJvmVersion() = metadata["java.version"].orEmpty()

        override fun getVendor() = metadata["java.vendor"].orEmpty()

        override fun getInstallationPath() = dir

        @Suppress("UnstableApiUsage")
        override fun isCurrentJvm() = false
    }

    override fun getExecutablePath() = dir.file(dir.asPath.resolveJavaRuntimeExecutable().pathString)
}
