// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

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
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.OpenedPackages
import kotlin.io.path.exists
import kotlin.io.path.readLines

class IntelliJPlatformArgumentProvider(
    @InputFiles @PathSensitive(RELATIVE) val intellijPlatform: ConfigurableFileCollection,
    @InputFile @PathSensitive(RELATIVE) val coroutinesJavaAgentFile: RegularFileProperty,
    private val options: JavaForkOptions,
//    private val requirePluginIds: List<String> = emptyList(), TODO: see #87
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
            productInfo.productCode.toIntelliJPlatformType() in listOf(
                IntelliJPlatformType.IntellijIdeaCommunity,
                IntelliJPlatformType.IntellijIdeaUltimate,
            )
        }

    private val currentLaunchProperties
        get() = intellijPlatformPath
            .productInfo()
            .currentLaunch
            .additionalJvmArguments
            .filter { it.startsWith("-D") }
            .map { it.resolveIdeHomeVariable(intellijPlatformPath) }

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
        (defaultHeapSpace + bootclasspath + vmOptions + kotlinxCoroutinesJavaAgent + currentLaunchProperties + additionalJvmArguments + heapSpace)
            .filterNot { it.isNullOrBlank() }

    // TODO: check if necessary:
    //       + listOf("-Didea.required.plugins.id=${requirePluginIds.joinToString(",")}",)
}
