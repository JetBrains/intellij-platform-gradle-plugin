// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

abstract class IntelliJPlatformProjectsService : BuildService<BuildServiceParameters.None> {

    private val moduleProjects = ConcurrentHashMap.newKeySet<String>()
    private val pluginProjects = ConcurrentHashMap.newKeySet<String>()
    private val platformPaths = ConcurrentHashMap<String, Provider<Path>>()

    fun markModuleProject(projectPath: String) {
        moduleProjects += projectPath
    }

    fun markPluginProject(projectPath: String) {
        pluginProjects += projectPath
    }

    fun isPluginProject(projectPath: String) = projectPath in pluginProjects

    fun isPureModuleProject(projectPath: String) = projectPath in moduleProjects && projectPath !in pluginProjects

    fun setPlatformPathProvider(projectPath: String, provider: Provider<Path>) {
        platformPaths.putIfAbsent(projectPath, provider)
    }

    fun getPlatformPathProvider(projectPath: String) = platformPaths[projectPath]
}
