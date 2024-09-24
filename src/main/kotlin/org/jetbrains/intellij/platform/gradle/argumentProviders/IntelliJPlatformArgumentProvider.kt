// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.launchFor
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.models.resolveIdeHomeVariable
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.platformPath
import kotlin.io.path.exists
import kotlin.io.path.readLines

/**
 * Provides command line arguments for launching IntelliJ Platform locally.
 *
 * @property intellijPlatformConfiguration IntelliJ Platform configuration files.
 * @property coroutinesJavaAgentFile Coroutines Java agent file.
 * @property runtimeArchProvider The provider for the Java Runtime architecture.
 * @property options The Java fork options.
 */
class IntelliJPlatformArgumentProvider(
    @InputFiles
    @PathSensitive(RELATIVE)
    val intellijPlatformConfiguration: FileCollection,

    @Classpath
    @Optional
    val coroutinesJavaAgentFile: Provider<RegularFile>,

    @Input
    val runtimeArchProvider: Provider<String>,

    private val options: JavaForkOptions,
) : CommandLineArgumentProvider {

    private val platformPath
        get() = intellijPlatformConfiguration.platformPath()

    private val productInfo
        get() = platformPath.productInfo()

    private val launch = runtimeArchProvider.map {
        productInfo.launchFor(it)
    }

    /**
     * Adds the `lib/boot.jar` present in the IntelliJ Platform SDK to be included in the JVM boot classpath.
     */
    private val bootclasspath
        get() = platformPath
            .resolve("lib/boot.jar")
            .takeIf { it.exists() }
            ?.let { listOf("-Xbootclasspath/a:$it") }
            .orEmpty()

    /**
     * Retrieves VM options from a dedicated file delivered with the IntelliJ Platform.
     * The file path is retrieved from the [ProductInfo.Launch.vmOptionsFilePath] property.
     * If present, the `kotlinx.coroutines.debug=off` is dropped.
     */
    private val vmOptions
        get() = launch.get()
            .vmOptionsFilePath
            ?.removePrefix("../")
            ?.let { platformPath.resolve(it).readLines() }
            .orEmpty()
            .filter { !it.contains("kotlinx.coroutines.debug=off") }

    /**
     * Applies the Java Agent pointing at the KotlinX Coroutines to enable coroutines debugging.
     */
    private val kotlinxCoroutinesJavaAgent
        get() = coroutinesJavaAgentFile.orNull
            ?.takeIf { it.asPath.exists() }
            ?.takeIf {
                productInfo.productCode.toIntelliJPlatformType() in listOf(
                    IntelliJPlatformType.IntellijIdeaCommunity,
                    IntelliJPlatformType.IntellijIdeaUltimate,
                )
            }
            ?.let { "-javaagent:${coroutinesJavaAgentFile.asPath}" }

    /**
     * Retrieves the additional JVM arguments from [ProductInfo.Launch.additionalJvmArguments].
     */
    private val additionalJvmArguments
        get() = launch.get()
            .additionalJvmArguments
            .map { it.resolveIdeHomeVariable(platformPath) }

    /**
     * Allows overriding default [vmOptions] heap size with values provided with [options].
     */
    private val heapSpace
        get() = listOfNotNull(
            options.maxHeapSize?.let { "-Xmx${it}" },
            options.minHeapSize?.let { "-Xms${it}" },
        )

    /**
     * Combines various arguments related to the IntelliJ Platform configuration to create a list of arguments to be passed to the platform.
     *
     * @return The list of arguments to be passed to the platform.
     */
    override fun asArguments() = (
            bootclasspath + vmOptions + kotlinxCoroutinesJavaAgent + additionalJvmArguments + heapSpace
            ).filterNot { it.isNullOrBlank() }
}
