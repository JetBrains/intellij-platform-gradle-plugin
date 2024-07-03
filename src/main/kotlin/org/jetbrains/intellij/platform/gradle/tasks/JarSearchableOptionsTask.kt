// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.aware.PluginAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import kotlin.io.path.exists

private const val SEARCHABLE_OPTIONS_SUFFIX = ".searchableOptions.xml"

/**
 * Creates a JAR file with searchable options to be distributed with the plugin.
 */
@CacheableTask
abstract class JarSearchableOptionsTask : Jar(), PluginAware {

    /**
     * The directory from which the prepared searchable options are read.
     *
     * Default value: [BuildSearchableOptionsTask.outputDirectory]
     */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty

    /**
     * Emit a warning if no searchable options are found.
     * Can be disabled with [BuildFeature.NO_SEARCHABLE_OPTIONS_WARNING].
     */
    @get:Internal
    abstract val noSearchableOptionsWarning: Property<Boolean>

    private val log = Logger(javaClass)

    @TaskAction
    override fun copy() {
        super.copy()

        if (noSearchableOptionsWarning.get()) {
            val noSearchableOptions = source.none {
                it.name.endsWith(SEARCHABLE_OPTIONS_SUFFIX)
            }
            if (noSearchableOptions) {
                log.warn(
                    "No searchable options found. If the plugin does not provide custom settings, " +
                            "disable building searchable options to improve build performance. " +
                            "See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-buildSearchableOptions"
                )
            }
        }
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Creates a JAR file with searchable options to be distributed with the plugin."

        includeEmptyDirs = false
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<JarSearchableOptionsTask>(Tasks.JAR_SEARCHABLE_OPTIONS) {
                val buildSearchableOptionsTaskProvider = project.tasks.named<BuildSearchableOptionsTask>(Tasks.BUILD_SEARCHABLE_OPTIONS)
                val buildSearchableOptionsEnabled = project.extensionProvider
                    .flatMap { it.buildSearchableOptions }
                    .zip(buildSearchableOptionsTaskProvider) { enabled, task ->
                        enabled && task.enabled
                    }
                val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)
                val libContainerProvider = prepareSandboxTaskProvider.flatMap {
                    it.pluginDirectory.dir("lib")
                }
                val runtimeElementsConfiguration = project.configurations[Configurations.External.RUNTIME_ELEMENTS]

                inputDirectory.convention(buildSearchableOptionsTaskProvider.flatMap { it.outputDirectory })
                archiveClassifier.convention("searchableOptions")
                destinationDirectory.convention(project.layout.buildDirectory.dir("libs"))
                noSearchableOptionsWarning.convention(BuildFeature.NO_SEARCHABLE_OPTIONS_WARNING.isEnabled(project.providers))

                from(inputDirectory)
                include {
                    when {
                        it.isDirectory -> true
                        !it.name.endsWith(SEARCHABLE_OPTIONS_SUFFIX) -> false
                        else -> libContainerProvider.asPath.resolve(it.name.removeSuffix(SEARCHABLE_OPTIONS_SUFFIX)).exists()
                    }
                }
                eachFile {
                    path = "search/$name"
                }

                onlyIf {
                    buildSearchableOptionsEnabled.get()
                }

                project.artifacts.add(runtimeElementsConfiguration.name, archiveFile)
            }
    }
}
