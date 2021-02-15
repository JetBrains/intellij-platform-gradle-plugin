package org.jetbrains.intellij.dependency

import java.io.File

interface PluginsRepository {

    fun resolve(plugin: PluginDependencyNotation): File?

    fun postResolve()
}
