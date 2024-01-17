// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import com.jetbrains.plugin.structure.base.utils.exists
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.asPath
import org.jetbrains.intellij.platform.gradle.model.ProductInfo
import org.jetbrains.intellij.platform.gradle.model.launchFor
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.utils.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.OpenedPackages
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.io.path.readLines

class IntelliJPlatformArgumentProvider(
    @InputFiles @PathSensitive(RELATIVE) val intellijPlatformConfiguration: ConfigurableFileCollection,
    @InputFile @PathSensitive(RELATIVE) val coroutinesJavaAgentFile: RegularFileProperty,
    @Input val runtimeArchProvider: Provider<String>,
    private val options: JavaForkOptions,
//    private val requirePluginIds: List<String> = emptyList(), TODO: see #87
) : CommandLineArgumentProvider {

    private val platformPath: Path
        get() = intellijPlatformConfiguration.single().toPath()

    private val productInfo: ProductInfo
        get() = platformPath.productInfo()

    private val launch = runtimeArchProvider.map {
        productInfo.launchFor(it)
    }

    private val bootclasspath
        get() = platformPath
            .resolve("lib/boot.jar")
            .takeIf { it.exists() }
            ?.let { listOf("-Xbootclasspath/a:$it") }
            .orEmpty()

    private val vmOptions
        get() = launch.get()
            .vmOptionsFilePath
            ?.removePrefix("../")
            ?.let { platformPath.resolve(it).readLines() }
            .orEmpty()
            .filter { !it.contains("kotlinx.coroutines.debug=off") }

    private val kotlinxCoroutinesJavaAgent
        get() = "-javaagent:${coroutinesJavaAgentFile.asPath}".takeIf {
            productInfo.productCode.toIntelliJPlatformType() in listOf(
                IntelliJPlatformType.IntellijIdeaCommunity,
                IntelliJPlatformType.IntellijIdeaUltimate,
            )
        }

    private val launchProperties
        get() = launch.get()
            .additionalJvmArguments
            .filter { it.startsWith("-D") }
            .map { it.resolveIdeHomeVariable() }

    private val additionalJvmArguments
        get() = launch.get()
            .additionalJvmArguments
            .filterNot { it.startsWith("-D") }
            .takeIf { it.isNotEmpty() }
            ?.map { it.resolveIdeHomeVariable() }
            ?: OpenedPackages

    private val defaultHeapSpace = listOf("-Xmx512m", "-Xms256m")
    private val heapSpace
        get() = listOfNotNull(
            options.maxHeapSize?.let { "-Xmx${it}" },
            options.minHeapSize?.let { "-Xms${it}" },
        )

    private fun String.resolveIdeHomeVariable() =
        platformPath.pathString.let { idePath ->
            this
                .replace("\$APP_PACKAGE", idePath)
                .replace("\$IDE_HOME", idePath)
                .replace("%IDE_HOME%", idePath)
                .replace("Contents/Contents", "Contents")
                .let { entry ->
                    val (_, value) = entry.split("=")
                    when {
                        Path(value).exists() -> entry
                        else -> entry.replace("/Contents", "")
                    }
                }
        }

    override fun asArguments() =
        (defaultHeapSpace + bootclasspath + vmOptions + kotlinxCoroutinesJavaAgent + launchProperties + additionalJvmArguments + heapSpace)
            .filterNot { it.isNullOrBlank() }

    // TODO: check if necessary:
    //       + listOf("-Didea.required.plugins.id=${requirePluginIds.joinToString(",")}",)
}
