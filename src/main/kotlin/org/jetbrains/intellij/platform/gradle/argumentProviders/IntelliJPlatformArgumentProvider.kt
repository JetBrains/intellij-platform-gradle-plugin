// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.jetbrains.intellij.platform.gradle.model.ProductInfo
import org.jetbrains.intellij.platform.gradle.model.launchFor
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString
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
    @InputFiles @PathSensitive(RELATIVE) val intellijPlatformConfiguration: ConfigurableFileCollection,
    @InputFile @PathSensitive(RELATIVE) val coroutinesJavaAgentFile: RegularFileProperty,
    @Input val runtimeArchProvider: Provider<String>,
    private val options: JavaForkOptions,
    private val requirePluginIds: List<String> = emptyList(),
) : CommandLineArgumentProvider {

    private val platformPath: Path
        get() = intellijPlatformConfiguration.single().toPath()

    private val productInfo: ProductInfo
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
     * Retrieves VM options from A dedicated file delivered with the IntelliJ Platform.
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
        get() = "-javaagent:${coroutinesJavaAgentFile.asPath}".takeIf {
            productInfo.productCode.toIntelliJPlatformType() in listOf(
                IntelliJPlatformType.IntellijIdeaCommunity,
                IntelliJPlatformType.IntellijIdeaUltimate,
            )
        }

    private val requiredPlugins
        get() = "-Didea.required.plugins.id=${requirePluginIds.joinToString(",")}"

    /**
     * Retrieves the additional JVM arguments from [ProductInfo.Launch.additionalJvmArguments].
     */
    private val additionalJvmArguments
        get() = launch.get()
            .additionalJvmArguments
            .map { it.resolveIdeHomeVariable() }

    /**
     * Allows overriding default [vmOptions] heap size with values provided with [options].
     */
    private val heapSpace
        get() = listOfNotNull(
            options.maxHeapSize?.let { "-Xmx${it}" },
            options.minHeapSize?.let { "-Xms${it}" },
        )

    /**
     * Resolves the IDE home variable in the given string by replacing placeholders.
     */
    private fun String.resolveIdeHomeVariable() =
        platformPath.pathString.let {
            this
                .replace("\$APP_PACKAGE", it)
                .replace("\$IDE_HOME", it)
                .replace("%IDE_HOME%", it)
                .replace("Contents/Contents", "Contents")
                .let { entry ->
                    val (_, value) = entry.split("=")
                    when {
                        Path(value).exists() -> entry
                        else -> entry.replace("/Contents", "")
                    }
                }
        }

    /**
     * Combines various arguments related to the IntelliJ Platform configuration to create a list of arguments to be passed to the platform.
     *
     * @return The list of arguments to be passed to the platform.
     */
    override fun asArguments() = (
            bootclasspath + vmOptions + kotlinxCoroutinesJavaAgent + requiredPlugins + additionalJvmArguments + heapSpace
            ).filterNot { it.isNullOrBlank() }
}
