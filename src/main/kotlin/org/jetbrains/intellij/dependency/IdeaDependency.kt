// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import org.jetbrains.intellij.collectJars
import org.jetbrains.intellij.collectZips
import org.jetbrains.intellij.isKotlinRuntime
import java.io.File
import java.io.Serializable
import kotlin.io.path.isDirectory
import kotlin.io.path.name

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
    val sourceZipFiles = this.collectSourceZipFiles()

    protected open fun collectJarFiles(): Collection<File> {
        if (classes.isDirectory) {
            val lib = classes.toPath().resolve("lib")
            if (lib.isDirectory()) {
                val baseFiles = (collectJars(lib) { file ->
                    (withKotlin || !isKotlinRuntime(file.name.removeSuffix(".jar"))) && file.name != "junit.jar" && file.name != "annotations.jar"
                }).sorted()
                val antFiles = collectJars(lib.resolve("ant/lib")).sorted()
                return (baseFiles + antFiles).map { it.toFile() }
            }
        }
        return emptyList()
    }

    private fun collectSourceZipFiles(): Collection<File> {
        if (classes.isDirectory) {
            return collectZips(classes.toPath().resolve("lib/src")).map { it.toFile() }
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
        if (sourceZipFiles != other.sourceZipFiles) return false

        return true
    }

    @Suppress("DuplicatedCode")
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
        result = 31 * result + sourceZipFiles.hashCode()
        return result
    }
}
