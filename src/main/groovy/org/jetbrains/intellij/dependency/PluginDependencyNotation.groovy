package org.jetbrains.intellij.dependency

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

@CompileStatic
class PluginDependencyNotation {
    @NotNull
    final String id

    @Nullable
    final String version

    @Nullable
    final String channel

    PluginDependencyNotation(@NotNull String id, @Nullable String version, @Nullable String channel) {
        this.id = id
        this.version = version
        this.channel = channel
    }

    Dependency toDependency(Project project) {
        return project.dependencies.create(toString())
    }

    @Override
    String toString() {
        def groupPrefix = channel ? "$channel." : ""
        return "${groupPrefix}com.jetbrains.plugins:$id:$version"
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        PluginDependencyNotation that = (PluginDependencyNotation) o

        if (channel != that.channel) return false
        if (id != that.id) return false
        if (version != that.version) return false

        return true
    }

    int hashCode() {
        int result
        result = id.hashCode()
        result = 31 * result + (version != null ? version.hashCode() : 0)
        result = 31 * result + (channel != null ? channel.hashCode() : 0)
        return result
    }

    @NotNull
    static PluginDependencyNotation parsePluginDependencyString(@NotNull String s) {
        if (new File(s).exists()) {
            return new PluginDependencyNotation(s, null, null)
        }

        String id = null, version = null, channel = null
        def idAndVersion = s.split('[:]', 2)
        if (idAndVersion.length == 1) {
            def idAndChannel = idAndVersion[0].split('[@]', 2)
            id = idAndChannel[0]
            channel = idAndChannel.length > 1 ? idAndChannel[1] : null
        } else if (idAndVersion.length == 2) {
            def versionAndChannel = idAndVersion[1].split('[@]', 2)
            id = idAndVersion[0]
            version = versionAndChannel[0]
            channel = versionAndChannel.length > 1 ? versionAndChannel[1] : null
        }
        return new PluginDependencyNotation(id ?: null, version ?: null, channel ?: null)
    }

// TODO: Replace with the following Kotlin snippet
//
//    fun parsePluginDependencyString(s: String): PluginDependencyNotation {
//        if (File(s).exists()) {
//            return PluginDependencyNotation(s, null, null)
//        }
//
//        val (idVersion, channel) = s.split('@', limit = 2) + null
//        val (id, version) = (idVersion ?: s).split(':', limit = 2) + null
//        return PluginDependencyNotation(id ?: s, version, channel)
//    }
}
