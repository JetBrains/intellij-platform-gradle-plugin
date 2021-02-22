package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import groovy.transform.CompileStatic
import java.io.File
import java.io.Serializable

@CompileStatic
interface PluginDependency : Serializable {

    fun getId(): String

    fun getVersion(): String

    fun getChannel(): String?

    fun getArtifact(): File

    fun getJarFiles(): Collection<File>

    fun getClassesDirectory(): File?

    fun getMetaInfDirectory(): File?

    fun getSourcesDirectory(): File?

    fun isBuiltin(): Boolean

    fun isMaven(): Boolean

    fun isCompatible(ideVersion: IdeVersion): Boolean

    fun getNotation(): PluginDependencyNotation
}
