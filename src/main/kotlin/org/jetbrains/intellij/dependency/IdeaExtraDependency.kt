package org.jetbrains.intellij.dependency

import org.jetbrains.intellij.collectJars
import java.io.File

class IdeaExtraDependency(val name: String, val classes: File) {

    val jarFiles = when {
        classes.isDirectory -> collectJars(classes) { true }
        else -> setOf(classes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IdeaExtraDependency

        if (name != other.name) return false
        if (classes != other.classes) return false
        if (jarFiles != other.jarFiles) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + classes.hashCode()
        result = 31 * result + jarFiles.hashCode()
        return result
    }
}
