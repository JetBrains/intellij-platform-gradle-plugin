package org.jetbrains.intellij.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.jvm.tasks.Jar
import java.io.File
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class JarSearchableOptionsTask @Inject constructor(
    objectFactory: ObjectFactory,
) : Jar() {

    @OutputDirectory
    @Optional
    val outputDir: DirectoryProperty = objectFactory.directoryProperty()

    @Input
    @Optional
    val pluginName: Property<String> = objectFactory.property(String::class.java)

    @Input
    @Optional
    val sandboxDir: Property<String> = objectFactory.property(String::class.java)

    init {
        val pluginJarFiles = mutableSetOf<String>()

        this.from({
            include {
                when {
                    it.isDirectory -> true
                    else -> {
                        val suffix = ".searchableOptions.xml"
                        if (it.name.endsWith(suffix) && pluginJarFiles.isEmpty()) {
                            File(sandboxDir.get(), "${pluginName.get()}/lib").list()?.let { files ->
                                pluginJarFiles.addAll(files)
                            }
                        }
                        val jarName = it.name.replace(suffix, "")
                        pluginJarFiles.contains(jarName)
                    }
                }
            }
            outputDir.get().asFile.canonicalPath
        })

        this.eachFile { it.path = "search/${it.name}" }
        includeEmptyDirs = false
    }
}
