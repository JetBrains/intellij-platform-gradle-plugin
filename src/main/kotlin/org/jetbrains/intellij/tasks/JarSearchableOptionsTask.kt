// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.property
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
    val pluginName = objectFactory.property<String>()

    @Input
    @Optional
    val sandboxDir = objectFactory.property<String>()

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

        this.eachFile { path = "search/$name" }
        includeEmptyDirs = false
    }
}
