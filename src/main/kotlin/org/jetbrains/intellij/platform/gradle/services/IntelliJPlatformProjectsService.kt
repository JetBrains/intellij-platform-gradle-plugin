// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.PluginInstallationTarget
import org.jetbrains.intellij.platform.gradle.tasks.aware.inferredPluginInstallationTarget
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

abstract class IntelliJPlatformProjectsService : BuildService<BuildServiceParameters.None> {

    private val moduleProjects = ConcurrentHashMap.newKeySet<String>()
    private val pluginProjects = ConcurrentHashMap.newKeySet<String>()
    private val modulePluginInstallationTargets = ConcurrentHashMap<String, Provider<PluginInstallationTarget>>()
    private val moduleBundledModuleProviders = ConcurrentHashMap<String, MutableSet<Provider<List<String>>>>()
    private val platformPaths = ConcurrentHashMap<String, Provider<Path>>()

    fun markModuleProject(projectPath: String) {
        moduleProjects += projectPath
    }

    fun markPluginProject(projectPath: String) {
        pluginProjects += projectPath
    }

    fun isPluginProject(projectPath: String) = projectPath in pluginProjects

    fun isPureModuleProject(projectPath: String) = projectPath in moduleProjects && projectPath !in pluginProjects

    internal fun registerModulePluginInstallationTarget(
        projectPath: String,
        pluginInstallationTarget: Provider<PluginInstallationTarget>,
    ) {
        modulePluginInstallationTargets.putIfAbsent(projectPath, pluginInstallationTarget)
    }

    internal fun registerModuleBundledModules(projectPath: String, bundledModules: Provider<List<String>>) {
        moduleBundledModuleProviders
            .computeIfAbsent(projectPath) { ConcurrentHashMap.newKeySet() }
            .add(bundledModules)
    }

    internal fun resolveModulePluginInstallationTarget(
        projectPath: String,
        rootPluginInstallationTarget: PluginInstallationTarget,
    ) = modulePluginInstallationTargets[projectPath]?.orNull
        ?: rootPluginInstallationTarget.takeUnless { it == PluginInstallationTarget.BOTH }
        ?: moduleBundledModuleProviders[projectPath]
            ?.flatMap { it.orNull.orEmpty() }
            ?.inferredPluginInstallationTarget()
        ?: PluginInstallationTarget.BACKEND

    fun setPlatformPathProvider(projectPath: String, provider: Provider<Path>) {
        platformPaths.putIfAbsent(projectPath, provider)
    }

    fun getPlatformPathProvider(projectPath: String) = platformPaths[projectPath]
}
