// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.listFiles
import com.jetbrains.plugin.structure.base.utils.simpleName
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.tasks.Jar
import org.jetbrains.intellij.IntelliJPluginConstants.SEARCHABLE_OPTIONS_SUFFIX
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.warn
import java.nio.file.Path

abstract class JarSearchableOptionsTask : Jar() {

    /**
     * The output directory where the JAR file will be created.
     *
     * Default value: `build/searchableOptions`
     */
    @get:OutputDirectory
    @get:Optional
    abstract val outputDir: DirectoryProperty

    /**
     * The name of the plugin.
     *
     * Default value: [org.jetbrains.intellij.IntelliJPluginExtension.pluginName]
     */
    @get:Input
    @get:Optional
    abstract val pluginName: Property<String>

    /**
     * The sandbox output directory.
     *
     * Default value: [org.jetbrains.intellij.tasks.PrepareSandboxTask.getDestinationDir]
     */
    @get:Input
    @get:Optional
    abstract val sandboxDir: Property<String>

    /**
     * Emit warning if no searchable options are found.
     * Can be disabled with [org.jetbrains.intellij.BuildFeature.NO_SEARCHABLE_OPTIONS_WARNING].
     */
    @get:Internal
    abstract val noSearchableOptionsWarning: Property<Boolean>

    private val context = logCategory()

    init {
        val pluginJarFiles = mutableSetOf<String>()

        this.from({
            include { it ->
                when {
                    it.isDirectory -> true
                    else -> {
                        if (it.name.endsWith(SEARCHABLE_OPTIONS_SUFFIX) && pluginJarFiles.isEmpty()) {
                            Path.of(sandboxDir.get())
                                .resolve(pluginName.get())
                                .resolve("lib")
                                .listFiles()
                                .map(Path::simpleName)
                                .let(pluginJarFiles::addAll)
                        }
                        it.name
                            .replace(SEARCHABLE_OPTIONS_SUFFIX, "")
                            .let(pluginJarFiles::contains)
                    }
                }
            }
            outputDir.get().asFile.toPath().toAbsolutePath()
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
