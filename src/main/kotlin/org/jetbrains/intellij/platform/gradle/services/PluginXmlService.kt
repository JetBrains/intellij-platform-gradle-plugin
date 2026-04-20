// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import com.jetbrains.plugin.structure.intellij.beans.PluginBean
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.intellij.platform.gradle.tasks.aware.parsePluginXml
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.readAttributes

private data class PluginXmlKey(
    val path: String,
    val size: Long,
    val lastModifiedTime: Long,
)

abstract class PluginXmlService : BuildService<BuildServiceParameters.None> {

    private val pluginXmls = ConcurrentHashMap<PluginXmlKey, PluginBean>()

    fun resolve(
        path: Path,
        parser: (Path) -> PluginBean = Path::parsePluginXml,
    ): PluginBean {
        val attributes = path.readAttributes<BasicFileAttributes>()
        val key = PluginXmlKey(
            path = path.safePathString,
            size = attributes.size(),
            lastModifiedTime = attributes.lastModifiedTime().toMillis(),
        )

        return pluginXmls.computeIfAbsent(key) {
            parser(path)
        }
    }
}

internal fun Project.pluginXmlService() = gradle.registerClassLoaderScopedBuildService(PluginXmlService::class)
