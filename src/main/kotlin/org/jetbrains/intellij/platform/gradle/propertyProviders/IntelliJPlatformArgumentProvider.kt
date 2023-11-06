// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.propertyProviders

import com.jetbrains.plugin.structure.base.utils.exists
import com.jetbrains.plugin.structure.base.utils.readLines
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.asPath
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.resolveIdeHomeVariable
import org.jetbrains.intellij.platform.gradle.utils.OpenedPackages

class IntelliJPlatformArgumentProvider(
    @InputFiles @PathSensitive(RELATIVE) val intellijPlatform: ConfigurableFileCollection,
    @InputFile @PathSensitive(RELATIVE) val coroutinesJavaAgentFile: RegularFileProperty,
    private val options: JavaForkOptions,
) : CommandLineArgumentProvider {

    private val intellijPlatformPath
        get() = intellijPlatform.singleFile.toPath()

    private val productInfo by lazy {
        intellijPlatformPath.productInfo()
    }

    private val bootclasspath
        get() = intellijPlatformPath
            .resolve("lib/boot.jar")
            .takeIf { it.exists() }
            ?.let { listOf("-Xbootclasspath/a:$it") }
            .orEmpty()

    private val vmOptions
        get() = productInfo
            .currentLaunch
            .vmOptionsFilePath
            ?.removePrefix("../")
            ?.let { intellijPlatformPath.resolve(it).readLines() }
            .orEmpty()
            .filter { !it.contains("kotlinx.coroutines.debug=off") }

    private val kotlinxCoroutinesJavaAgent
        get() = "-javaagent:${coroutinesJavaAgentFile.asPath}".takeIf {
            IntelliJPlatformType.fromCode(productInfo.productCode) in listOf(
                IntelliJPlatformType.IntellijIdeaCommunity,
                IntelliJPlatformType.IntellijIdeaUltimate,
            )
        }

    private val additionalJvmArguments
        get() = productInfo
            .currentLaunch
            .additionalJvmArguments
            .filterNot { it.startsWith("-D") }
            .takeIf { it.isNotEmpty() }
            ?.map { it.resolveIdeHomeVariable(intellijPlatformPath) }
            ?: OpenedPackages

    private val defaultHeapSpace = listOf("-Xmx512m", "-Xms256m")
    private val heapSpace
        get() = listOfNotNull(
            options.maxHeapSize?.let { "-Xmx${it}" },
            options.minHeapSize?.let { "-Xms${it}" },
        )

    override fun asArguments() =
        (defaultHeapSpace + bootclasspath + vmOptions + kotlinxCoroutinesJavaAgent + additionalJvmArguments + heapSpace)
            .filterNot { it.isNullOrBlank() }
}
