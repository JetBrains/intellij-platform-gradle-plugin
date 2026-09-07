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
import org.jetbrains.intellij.platform.gradle.models.*
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

    /**
     * Combines various arguments related to the IntelliJ Platform configuration to create a list of arguments to be passed to the platform.
     *
     * @return The list of arguments to be passed to the platform.
     */
    override fun asArguments(): List<String> {
        val platformPath = intellijPlatformConfiguration.platformPath()
        val productInfo = platformPath.productInfo()
        val launch = productInfo.launchFor(runtimeArchProvider.get())
        val heapSpaceOverrides = options.heapSpaceOverrides()

        val bootclasspath = platformPath
            .resolve("lib/boot.jar")
            .takeIf { it.exists() }
            ?.let { listOf("-Xbootclasspath/a:$it") }
            .orEmpty()

        val vmOptions = launch
            .vmOptionsFilePath
            ?.removePrefix("../")
            ?.let { platformPath.resolve(it).readLines() }
            .orEmpty()
            .filter { !it.contains("kotlinx.coroutines.debug=off") }
            .filterOverriddenHeapSpace(heapSpaceOverrides)

        val kotlinxCoroutinesJavaAgent = coroutinesJavaAgentFile.orNull
            ?.asPath
            ?.takeIf { it.exists() }
            ?.takeIf {
                productInfo.type in setOf(
                    IntelliJPlatformType.IntellijIdea,
                    IntelliJPlatformType.IntellijIdeaCommunity,
                    IntelliJPlatformType.IntellijIdeaUltimate,
                )
            }
            ?.let { "-javaagent:$it" }

        val additionalJvmArguments = launch
            .additionalJvmArguments
            .map { it.resolveIdeHomeVariable(platformPath) }

        return (bootclasspath + vmOptions + additionalJvmArguments + options.heapSpaceArguments() + listOfNotNull(kotlinxCoroutinesJavaAgent))
            .filterNot { it.isNullOrBlank() }
    }
}
