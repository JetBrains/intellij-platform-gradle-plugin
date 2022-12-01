// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import org.gradle.api.Project
import org.jetbrains.intellij.or
import java.io.File
import java.io.Serializable

class PluginDependencyNotation(val id: String, val version: String?, val channel: String?) : Serializable {

    companion object {
        fun parsePluginDependencyString(s: String): PluginDependencyNotation {
            if (File(s).exists()) {
                return PluginDependencyNotation(s, null, null)
            }

            val (idVersion, channel) = s.split('@', limit = 2) + null
            val (id, version) = idVersion.or(s).split(':', limit = 2) + null
            return PluginDependencyNotation(id.or(s), version?.takeIf(String::isNotEmpty), channel)
        }
    }

    fun toDependency(project: Project) = project.dependencies.create(toString())

    override fun toString(): String {
        val groupPrefix = channel.takeUnless { channel.isNullOrBlank() }?.let { "$it." }.orEmpty()
        return "${groupPrefix}com.jetbrains.plugins:$id:$version"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PluginDependencyNotation

        if (id != other.id) return false
        if (version != other.version) return false
        if (channel != other.channel) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (version?.hashCode() ?: 0)
        result = 31 * result + (channel?.hashCode() ?: 0)
        return result
    }
}
