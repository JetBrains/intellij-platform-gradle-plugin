package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import groovy.transform.CompileStatic
import java.io.File
import java.io.Serializable

@CompileStatic
interface PluginDependency : Serializable {

    val id: String

    val version: String

    val channel: String?

    val artifact: File

    val jarFiles: Collection<File>

    val classesDirectory: File?

    val metaInfDirectory: File?

    val sourcesDirectory: File?

    val builtin: Boolean

    val maven: Boolean

    val notation: PluginDependencyNotation

    fun isCompatible(ideVersion: IdeVersion): Boolean
}
