// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.base.utils.isJar
import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.jetbrains.intellij.collectJars
import java.io.File

internal class PluginDependencyImpl(
    override val id: String,
    override val platformPluginId: String?,
    override val version: String,
    override val artifact: File,
    override val builtin: Boolean = false,
    override val maven: Boolean = false,
) : PluginDependency {

    override var channel: String? = null
    override var jarFiles: Collection<File> = emptyList()
    override var sourceJarFiles: Collection<File> = emptyList()
    override var classesDirectory: File? = null
    override var metaInfDirectory: File? = null
    override val notation = PluginDependencyNotation(id, version, channel)
    var sinceBuild: String? = null
    var untilBuild: String? = null

    init {
        if (artifact.toPath().isJar()) {
            jarFiles = listOf(artifact)
        }
        if (artifact.isDirectory) {
            val lib = File(artifact, "lib")
            if (lib.isDirectory) {
                jarFiles = collectJars(lib)
                sourceJarFiles = collectJars(File(lib, "src"))
            }
            val classes = File(artifact, "classes")
            if (classes.isDirectory) {
                classesDirectory = classes
            }
            val metaInf = File(artifact, "META-INF")
            if (metaInf.isDirectory) {
                metaInfDirectory = metaInf
            }
        }
    }

    override fun isCompatible(ideVersion: IdeVersion) =
        sinceBuild?.let { IdeVersion.createIdeVersion(it) <= ideVersion } ?: true &&
                untilBuild?.let { ideVersion <= IdeVersion.createIdeVersion(it) } ?: true

    @Suppress("DuplicatedCode")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PluginDependencyImpl

        if (id != other.id) return false
        if (platformPluginId != other.platformPluginId) return false
        if (version != other.version) return false
        if (artifact != other.artifact) return false
        if (builtin != other.builtin) return false
        if (maven != other.maven) return false
        if (channel != other.channel) return false
        if (jarFiles != other.jarFiles) return false
        if (sourceJarFiles != other.sourceJarFiles) return false
        if (classesDirectory != other.classesDirectory) return false
        if (metaInfDirectory != other.metaInfDirectory) return false
        if (notation != other.notation) return false
        if (sinceBuild != other.sinceBuild) return false
        if (untilBuild != other.untilBuild) return false

        return true
    }

    @Suppress("DuplicatedCode")
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + platformPluginId.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + artifact.hashCode()
        result = 31 * result + builtin.hashCode()
        result = 31 * result + maven.hashCode()
        result = 31 * result + channel.hashCode()
        result = 31 * result + jarFiles.hashCode()
        result = 31 * result + sourceJarFiles.hashCode()
        result = 31 * result + classesDirectory.hashCode()
        result = 31 * result + metaInfDirectory.hashCode()
        result = 31 * result + notation.hashCode()
        result = 31 * result + sinceBuild.hashCode()
        result = 31 * result + untilBuild.hashCode()
        return result
    }
}
