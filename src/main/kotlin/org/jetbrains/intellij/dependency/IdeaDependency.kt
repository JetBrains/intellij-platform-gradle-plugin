package org.jetbrains.intellij.dependency

import org.jetbrains.intellij.collectJars
import org.jetbrains.intellij.isKotlinRuntime
import java.io.File
import java.io.Serializable

open class IdeaDependency(
    val name: String,
    val version: String,
    val buildNumber: String,
    val classes: File,
    val sources: File?,
    val withKotlin: Boolean,
    val pluginsRegistry: BuiltinPluginsRegistry,
    val extraDependencies: Collection<IdeaExtraDependency>,
) : Serializable {

    private val formatVersion = 2
    val jarFiles = this.collectJarFiles()

    protected open fun collectJarFiles(): Collection<File> {
        if (classes.isDirectory) {
            val lib = File(classes, "lib")
            if (lib.isDirectory) {
                val baseFiles = (collectJars(lib) { file ->
                    (withKotlin || !isKotlinRuntime(file.name.removeSuffix(".jar"))) && file.name != "junit.jar" && file.name != "annotations.jar"
                }).sorted()
                val antFiles = collectJars(File(lib, "ant/lib")) { true }.sorted()
                return baseFiles + antFiles
            }
        }
        return emptyList()
    }

    open fun getIvyRepositoryDirectory(): File? = classes

    fun getFqn(): String {
        var fqn = "$name-$version-$formatVersion"
        if (withKotlin) {
            fqn += "-withKotlin"
        }
        if (sources != null) {
            fqn += "-withSources"
        }
        return fqn
    }

    @Suppress("DuplicatedCode")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IdeaDependency

        if (name != other.name) return false
        if (version != other.version) return false
        if (buildNumber != other.buildNumber) return false
        if (classes != other.classes) return false
        if (sources != other.sources) return false
        if (withKotlin != other.withKotlin) return false
        if (pluginsRegistry != other.pluginsRegistry) return false
        if (extraDependencies != other.extraDependencies) return false
        if (jarFiles != other.jarFiles) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + buildNumber.hashCode()
        result = 31 * result + classes.hashCode()
        result = 31 * result + sources.hashCode()
        result = 31 * result + withKotlin.hashCode()
        result = 31 * result + pluginsRegistry.hashCode()
        result = 31 * result + extraDependencies.hashCode()
        result = 31 * result + jarFiles.hashCode()
        return result
    }
}
