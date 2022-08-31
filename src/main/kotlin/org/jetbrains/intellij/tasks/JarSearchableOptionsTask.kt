// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.property
import org.jetbrains.intellij.IntelliJPluginConstants.SEARCHABLE_OPTIONS_SUFFIX
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.warn
import java.io.File
import javax.inject.Inject

open class JarSearchableOptionsTask @Inject constructor(
    objectFactory: ObjectFactory,
) : Jar() {

    /**
     * The output directory where the JAR file will be created.
     *
     * Default value: `build/searchableOptions`
     */
    @get:OutputDirectory
    @get:Optional
    val outputDir = objectFactory.directoryProperty()

    /**
     * The name of the plugin.
     *
     * Default value: [org.jetbrains.intellij.IntelliJPluginExtension.pluginName]
     */
    @get:Input
    @get:Optional
    val pluginName = objectFactory.property<String>()

    /**
     * The sandbox output directory.
     *
     * Default value: [org.jetbrains.intellij.tasks.PrepareSandboxTask.getDestinationDir]
     */
    @get:Input
    @get:Optional
    val sandboxDir = objectFactory.property<String>()

    /**
     * Emit warning if no searchable options are found.
     * Can be disabled with [org.jetbrains.intellij.BuildFeature.NO_SEARCHABLE_OPTIONS_WARNING].
     */
    @get:Internal
    val noSearchableOptionsWarning = objectFactory.property<Boolean>()

    private val context = logCategory()

    init {
        val pluginJarFiles = mutableSetOf<String>()

        this.from({
            include {
                when {
                    it.isDirectory -> true
                    else -> {
                        if (it.name.endsWith(SEARCHABLE_OPTIONS_SUFFIX) && pluginJarFiles.isEmpty()) {
                            File(sandboxDir.get(), "${pluginName.get()}/lib").list()?.let { files ->
                                pluginJarFiles.addAll(files)
                            }
                        }
                        val jarName = it.name.replace(SEARCHABLE_OPTIONS_SUFFIX, "")
                        pluginJarFiles.contains(jarName)
                    }
                }
            }
            outputDir.get().asFile.canonicalPath
        })

        this.eachFile { path = "search/$name" }
        includeEmptyDirs = false
    }

    @TaskAction
    override fun copy() {
        super.copy()

        if (noSearchableOptionsWarning.get()) {
            val noSearchableOptions = source.none {
                it.name.endsWith(SEARCHABLE_OPTIONS_SUFFIX)
            }
            if (noSearchableOptions) {
                warn(
                    context,
                    "No searchable options found. If plugin is not supposed to provide custom settings exposed in UI, " +
                        "disable building searchable options to decrease the build time. " +
                        "See: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-options"
                )
            }
        }
    }
}
