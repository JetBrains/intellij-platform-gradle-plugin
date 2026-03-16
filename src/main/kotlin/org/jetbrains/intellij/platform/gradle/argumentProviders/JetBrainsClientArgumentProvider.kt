// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import org.jetbrains.intellij.platform.gradle.tasks.SPLIT_MODE_FRONTEND_COMMAND
import org.jetbrains.intellij.platform.gradle.models.customCommandFor
import org.jetbrains.intellij.platform.gradle.models.frontendSplitRootModule
import org.jetbrains.intellij.platform.gradle.models.launchFor
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.models.resolveIdeHomeVariable
import org.jetbrains.intellij.platform.gradle.utils.platformPath
import kotlin.io.path.exists
import kotlin.io.path.readLines

/**
 * Provides the minimal JVM arguments required to launch JetBrains Client directly from a full IDE distribution.
 */
class JetBrainsClientArgumentProvider(
    @InputFiles
    @PathSensitive(RELATIVE)
    val intellijPlatformConfiguration: FileCollection,

    @Input
    val runtimeArchProvider: Provider<String>,

    private val options: JavaForkOptions,
) : CommandLineArgumentProvider {

    private fun String.isHostOnlyJvmArgument() =
        startsWith("-Xbootclasspath/a:") ||
            startsWith("-Djava.nio.file.spi.DefaultFileSystemProvider=")

    private fun String.isHeapSpaceOption() = startsWith("-Xmx") || startsWith("-Xms")

    private val heapSpace
        get() = listOfNotNull(
            options.maxHeapSize?.let { "-Xmx$it" },
            options.minHeapSize?.let { "-Xms$it" },
        )

    override fun asArguments(): List<String> {
        val platformPath = intellijPlatformConfiguration.platformPath()
        val productInfo = platformPath.productInfo()
        val launch = productInfo.launchFor(runtimeArchProvider.get())
        val frontendCommand = productInfo.customCommandFor(runtimeArchProvider.get(), SPLIT_MODE_FRONTEND_COMMAND)
        val inheritedJvmArguments = options.jvmArgs.orEmpty()
        val overridesDefaultMaxHeap = options.maxHeapSize != null || inheritedJvmArguments.any { it.startsWith("-Xmx") }
        val overridesDefaultMinHeap = options.minHeapSize != null || inheritedJvmArguments.any { it.startsWith("-Xms") }
        val jetBrainsClientVmOptionsPath = platformPath.resolve("bin/jetbrains_client.vmoptions")
        val vmOptionsPath = when {
            frontendCommand?.vmOptionsFilePath != null -> platformPath.resolve(frontendCommand.vmOptionsFilePath.removePrefix("../"))
            jetBrainsClientVmOptionsPath.exists() -> jetBrainsClientVmOptionsPath
            else -> launch
                .vmOptionsFilePath
                ?.removePrefix("../")
                ?.let { platformPath.resolve(it) }
        }

        val vmOptions = vmOptionsPath
            ?.readLines()
            .orEmpty()
            .filter { !it.contains("kotlinx.coroutines.debug=off") }
            .filterNot { it.startsWith("-Djava.nio.file.spi.DefaultFileSystemProvider=") }
            .filterNot { overridesDefaultMaxHeap && it.startsWith("-Xmx") }
            .filterNot { overridesDefaultMinHeap && it.startsWith("-Xms") }

        val additionalJvmArguments = (frontendCommand?.additionalJvmArguments ?: launch.additionalJvmArguments)
            .map { it.resolveIdeHomeVariable(platformPath) }
            .filterNot { it.isHostOnlyJvmArgument() }

        val frontendJvmArguments = when (frontendCommand) {
            null -> listOf(
                "-Didea.platform.prefix=JetBrainsClient",
                "-Dintellij.platform.root.module=${productInfo.frontendSplitRootModule}",
                "-Dintellij.platform.product.mode=frontend",
                "-Didea.paths.customizer=com.intellij.platform.ide.impl.startup.multiProcess.FrontendProcessPathCustomizer",
                "-Dintellij.platform.full.ide.product.code=${productInfo.productCode}",
                "-Dide.no.platform.update=true",
                "-Didea.initially.ask.config=never",
                "-Dnosplash=true",
                "-Dintellij.platform.load.app.info.from.resources=true",
            )

            else -> emptyList()
        }

        return (vmOptions + additionalJvmArguments + frontendJvmArguments + heapSpace)
            .filterNot { it.isBlank() }
    }
}
