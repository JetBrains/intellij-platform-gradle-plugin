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
}
