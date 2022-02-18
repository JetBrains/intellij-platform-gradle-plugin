package org.jetbrains.intellij.performanceTest

import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginDependencyImpl
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import org.jetbrains.intellij.tasks.SetupDependenciesTask
import java.io.File

class PerfUtils {

    companion object {
        fun isPerfPluginPassedByUser(extension: IntelliJPluginExtension): Boolean {
            return extension.plugins.get()
                .find { it is String && it.startsWith(IntelliJPluginConstants.PERFORMANCE_PLUGIN_ID) } == null
        }

        fun get(setupDependenciesTask: SetupDependenciesTask): List<Pair<String,String>> {
             return PluginRepositoryFactory
                .create(IntelliJPluginConstants.MARKETPLACE_HOST, null)
                .pluginManager.searchCompatibleUpdates(
                    listOf(IntelliJPluginConstants.PERFORMANCE_PLUGIN_ID),
                    setupDependenciesTask.idea.get().buildNumber
                ).map { Pair(it.pluginXmlId,it.version) }
        }
    }
}