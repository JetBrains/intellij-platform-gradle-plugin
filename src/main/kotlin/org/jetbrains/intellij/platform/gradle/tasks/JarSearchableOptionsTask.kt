// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIRECTORY
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.SEARCHABLE_OPTIONS_SUFFIX
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.isBuildFeatureEnabled
import org.jetbrains.intellij.platform.gradle.tasks.aware.PluginAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SandboxAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.parse
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * Creates a JAR file with searchable options to be distributed with the plugin.
 */
@CacheableTask
abstract class JarSearchableOptionsTask : Jar(), SandboxAware, PluginAware {

    /**
     * The output directory where the JAR file will be created.
     *
     * Default value: [ProjectLayout.getBuildDirectory]/searchableOptions
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
                log.warn(
                    "No searchable options found. If plugin is not supposed to provide custom settings exposed in UI, " +
                            "disable building searchable options to decrease the build time. " +
                            "See: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-options"
                )
            }
        }
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<JarSearchableOptionsTask>(Tasks.JAR_SEARCHABLE_OPTIONS) {
                val extension = project.the<IntelliJPlatformExtension>()
                val buildSearchableOptionsTaskProvider = project.tasks.named<BuildSearchableOptionsTask>(Tasks.BUILD_SEARCHABLE_OPTIONS)
                val buildSearchableOptionsEnabled = extension.buildSearchableOptions.zip(buildSearchableOptionsTaskProvider) { enabled, task ->
                    enabled && task.enabled
                }

                inputDirectory.convention(buildSearchableOptionsTaskProvider.flatMap { it.outputDirectory.apply { asPath.createDirectories() } })
                archiveBaseName.convention("lib/$SEARCHABLE_OPTIONS_DIRECTORY")
                destinationDirectory.convention(project.layout.buildDirectory.dir("libsSearchableOptions")) // TODO: check if necessary, if so — use temp
                noSearchableOptionsWarning.convention(project.isBuildFeatureEnabled(BuildFeature.NO_SEARCHABLE_OPTIONS_WARNING))

                from(inputDirectory)

                include {
                    it
                        .takeIf { it.name.endsWith(SEARCHABLE_OPTIONS_SUFFIX) }
                        ?.run {
                            val pluginName = pluginXml.parse { name }.get()
                            sandboxPluginsDirectory.asPath
                                .resolve(pluginName)
                                .resolve("lib")
                                .resolve(name.removeSuffix(SEARCHABLE_OPTIONS_SUFFIX))
                        }
                        ?.exists()
                        ?: it.isDirectory
                }

                eachFile {
                    path = "search/$name"
                }

                onlyIf {
                    buildSearchableOptionsEnabled.get()
                }

                outputs.dir(destinationDirectory)

                dependsOn(buildSearchableOptionsTaskProvider)
            }
    }
}
