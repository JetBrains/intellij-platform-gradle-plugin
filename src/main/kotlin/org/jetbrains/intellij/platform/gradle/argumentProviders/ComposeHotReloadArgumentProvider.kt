// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.utils.safePathString

/**
 * Provides JVM arguments for enabling Compose Hot Reload functionality when running the IDE.
 *
 * This argument provider configures the necessary JVM flags to support hot reloading of Compose UI components
 * during development, including enabling enhanced class redefinition and attaching the Compose hot reload Java agent.
 *
 * @property composeHotReloadAgentConfiguration The file collection containing the Compose hot reload agent JAR.
 *           This configuration must resolve to exactly one JAR file.
 *
 * @throws InvalidUserDataException if the configuration doesn't resolve to exactly one JAR file.
 */
class ComposeHotReloadArgumentProvider(
    @InputFiles
    @PathSensitive(RELATIVE)
    val composeHotReloadAgentConfiguration: FileCollection,
) : CommandLineArgumentProvider {

    /**
     * The resolved Compose hot reload agent JAR file.
     *
     * @throws InvalidUserDataException if the configuration resolves to zero or multiple JAR files.
     */
    private val agentJar
        get() = composeHotReloadAgentConfiguration.singleOrNull()
            ?: throw InvalidUserDataException(
                "Unable to resolve compose hot reload agent JAR: [${composeHotReloadAgentConfiguration.joinToString(",")}]",
            )

    /**
     * Returns the JVM arguments required for Compose hot reload:
     * - `-XX:+AllowEnhancedClassRedefinition`: Enables enhanced class redefinition capability
     * - `-javaagent:<path>`: Attaches the Compose hot reload agent
     */
    override fun asArguments() = listOf(
        "-XX:+AllowEnhancedClassRedefinition",
        "-javaagent:${agentJar.toPath().safePathString}",
    )
}
