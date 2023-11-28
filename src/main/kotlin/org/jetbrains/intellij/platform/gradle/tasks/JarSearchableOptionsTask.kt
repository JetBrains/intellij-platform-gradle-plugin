// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.SEARCHABLE_OPTIONS_SUFFIX
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.base.SandboxAware
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Creates a JAR file with searchable options to be distributed with the plugin.
 */
@CacheableTask
abstract class JarSearchableOptionsTask : Jar(), SandboxAware {

    /**
     * The output directory where the JAR file will be created.
     *
     * Default value: `build/searchableOptions`
     */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    /**
     * The name of the plugin.
     *
     * Default value: [org.jetbrains.intellij.platform.gradle.IntelliJPluginExtension.pluginName]
     */
    @get:Internal
    abstract val pluginName: Property<String>

    /**
     * Emit warning if no searchable options are found.
     * Can be disabled with [org.jetbrains.intellij.BuildFeature.NO_SEARCHABLE_OPTIONS_WARNING].
     */
    @get:Internal
    abstract val noSearchableOptionsWarning: Property<Boolean>

    private val context = logCategory()

    init {
        group = PLUGIN_GROUP_NAME
        description = "Creates a JAR file with searchable options to be distributed with the plugin."

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

    companion object {
        fun register(project: Project) =
            project.registerTask<JarSearchableOptionsTask>(Tasks.JAR_SEARCHABLE_OPTIONS) {
                val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)
                val buildSearchableOptionsTaskProvider = project.tasks.named<BuildSearchableOptionsTask>(Tasks.BUILD_SEARCHABLE_OPTIONS)
                val buildSearchableOptionsDidWork = buildSearchableOptionsTaskProvider.map { it.didWork }

                inputDir.convention(project.layout.buildDirectory.dir(IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME))
                pluginName.convention(prepareSandboxTaskProvider.flatMap { it.pluginName })
                archiveBaseName.convention("lib/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}")
                destinationDirectory.convention(project.layout.buildDirectory.dir("libsSearchableOptions")) // TODO: check if necessary, if so — use temp
                noSearchableOptionsWarning.convention(project.isBuildFeatureEnabled(BuildFeature.NO_SEARCHABLE_OPTIONS_WARNING))

                // TODO Test it!!!
                val pluginJarFiles = mutableSetOf<String>()
                from({
                    include {
                        when {
                            it.isDirectory -> true
                            else -> {
                                if (it.name.endsWith(SEARCHABLE_OPTIONS_SUFFIX) && pluginJarFiles.isEmpty()) {
                                    sandboxPluginsDirectory
                                        .asPath
                                        .resolve(pluginName.get())
                                        .resolve("lib")
                                        .listDirectoryEntries()
                                        .map { it.name }
                                        .let(pluginJarFiles::addAll)
                                }
                                it.name
                                    .replace(SEARCHABLE_OPTIONS_SUFFIX, "")
                                    .let(pluginJarFiles::contains)
                            }
                        }
                    }
                    inputDir.asPath
                })

                eachFile { path = "search/$name" }

                onlyIf {
                    buildSearchableOptionsDidWork.get() && inputDir.asPath.isDirectory()
                }

                dependsOn(prepareSandboxTaskProvider)
                dependsOn(buildSearchableOptionsTaskProvider)
            }
    }
}
