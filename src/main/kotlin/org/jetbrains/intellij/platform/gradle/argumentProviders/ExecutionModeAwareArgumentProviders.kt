// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware

/**
 * Selects the correct JVM argument provider for a [RunIdeTask.ExecutionMode]
 * while keeping the nested provider inputs visible to Gradle.
 */
internal class ExecutionModeAwareIdeArgumentProvider(
    intellijPlatformConfiguration: FileCollection,
    coroutinesJavaAgentFile: Provider<RegularFile>,
    runtimeArchProvider: Provider<String>,

    @Input
    val executionMode: Provider<RunIdeTask.ExecutionMode>,

    options: JavaForkOptions,
) : CommandLineArgumentProvider {

    @get:Nested
    val hostProvider = IntelliJPlatformArgumentProvider(
        intellijPlatformConfiguration = intellijPlatformConfiguration,
        coroutinesJavaAgentFile = coroutinesJavaAgentFile,
        runtimeArchProvider = runtimeArchProvider,
        options = options,
    )

    @get:Nested
    val frontendProvider = JetBrainsClientArgumentProvider(
        intellijPlatformConfiguration = intellijPlatformConfiguration,
        runtimeArchProvider = runtimeArchProvider,
        options = options,
    )

    override fun asArguments() = when (executionMode.get()) {
        RunIdeTask.ExecutionMode.STANDARD,
        RunIdeTask.ExecutionMode.SPLIT_MODE_BACKEND,
            -> hostProvider.asArguments()

        RunIdeTask.ExecutionMode.SPLIT_MODE_FRONTEND,
            -> frontendProvider.asArguments()
    }
}

/**
 * Selects the correct plugin requirement arguments for a [RunIdeTask.ExecutionMode]
 * while keeping nested provider inputs visible to Gradle.
 */
internal class ExecutionModeAwarePluginArgumentProvider(
    pluginXml: Provider<RegularFile>,

    @Input
    val executionMode: Provider<RunIdeTask.ExecutionMode>,

    @Input
    val splitMode: Provider<Boolean>,

    @Input
    val pluginInstallationTarget: Provider<SplitModeAware.PluginInstallationTarget>,
) : CommandLineArgumentProvider {

    @get:Nested
    val standardProvider = PluginArgumentProvider(pluginXml)

    @get:Nested
    val backendProvider = SplitModePluginArgumentProvider(
        delegate = PluginArgumentProvider(pluginXml),
        pluginInstallationTarget = pluginInstallationTarget,
        requiredTarget = SplitModeAware.PluginInstallationTarget.BACKEND,
    )

    @get:Nested
    val frontendProvider = SplitModePluginArgumentProvider(
        delegate = PluginArgumentProvider(pluginXml),
        pluginInstallationTarget = pluginInstallationTarget,
        requiredTarget = SplitModeAware.PluginInstallationTarget.FRONTEND,
    )

    override fun asArguments() = when (executionMode.get()) {
        RunIdeTask.ExecutionMode.STANDARD -> when {
            !splitMode.get() -> standardProvider.asArguments()
            pluginInstallationTarget.get().includes(SplitModeAware.PluginInstallationTarget.BACKEND) -> standardProvider.asArguments()
            else -> emptyList()
        }
        RunIdeTask.ExecutionMode.SPLIT_MODE_BACKEND -> backendProvider.asArguments()
        RunIdeTask.ExecutionMode.SPLIT_MODE_FRONTEND -> frontendProvider.asArguments()
    }
}

/**
 * Selects the correct sandbox argument provider for a [RunIdeTask.ExecutionMode].
 */
internal class ExecutionModeAwareSandboxArgumentProvider(
    sandboxConfigDirectory: DirectoryProperty,
    sandboxPluginsDirectory: DirectoryProperty,
    sandboxSystemDirectory: DirectoryProperty,
    sandboxLogDirectory: DirectoryProperty,
    frontendPropertiesFile: Provider<RegularFile>,

    @Input
    val executionMode: Provider<RunIdeTask.ExecutionMode>,
) : CommandLineArgumentProvider {

    @get:Nested
    val hostProvider = SandboxArgumentProvider(
        sandboxConfigDirectory = sandboxConfigDirectory,
        sandboxPluginsDirectory = sandboxPluginsDirectory,
        sandboxSystemDirectory = sandboxSystemDirectory,
        sandboxLogDirectory = sandboxLogDirectory,
    )

    @get:Nested
    val frontendProvider = FrontendSandboxArgumentProvider(frontendPropertiesFile)

    override fun asArguments() = when (executionMode.get()) {
        RunIdeTask.ExecutionMode.STANDARD,
        RunIdeTask.ExecutionMode.SPLIT_MODE_BACKEND,
            -> hostProvider.asArguments()

        RunIdeTask.ExecutionMode.SPLIT_MODE_FRONTEND,
            -> frontendProvider.asArguments()
    }
}
