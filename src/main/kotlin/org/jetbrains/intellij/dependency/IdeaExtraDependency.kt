package org.jetbrains.intellij.dependency


import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.Utils

class IdeaExtraDependency {
    @NotNull
    private final String name
    @NotNull
    private final File classes
    @NotNull
    private final Collection<File> jarFiles

    IdeaExtraDependency(@NotNull String name, @NotNull File classes) {
        this.name = name
        this.classes = classes
        if (classes.isDirectory()) {
            this.jarFiles = Utils.collectJars(classes, { file -> true })
        }
        else {
            this.jarFiles = [classes] as Set
        }
    }

    @NotNull
    String getName() {
        return name
    }

    @NotNull
    File getClasses() {
        return classes
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    @NotNull
    Collection<File> getJarFiles() {
        return jarFiles
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (!(o instanceof IdeaExtraDependency)) return false
        IdeaExtraDependency that = (IdeaExtraDependency) o
        if (classes != that.classes) return false
        if (jarFiles != that.jarFiles) return false
        if (name != that.name) return false
        return true
    }

    int hashCode() {
        int result
        result = name.hashCode()
        result = 31 * result + classes.hashCode()
        result = 31 * result + jarFiles.hashCode()
        return result
    }
}
