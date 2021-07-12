package org.jetbrains.intellij.dependency

import org.gradle.api.Project
import java.io.File

interface PluginsRepository {

    fun resolve(project: Project, plugin: PluginDependencyNotation, context: String?): File?

    fun postResolve(project: Project, context: String?)
}
