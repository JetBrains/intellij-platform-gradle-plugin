// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.aware.parse
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import java.nio.file.FileSystems
import javax.inject.Inject
import kotlin.io.path.exists

internal const val SEARCHABLE_OPTIONS_SUFFIX_XML = ".searchableOptions.xml"
internal const val SEARCHABLE_OPTIONS_SUFFIX_JSON = "-searchableOptions.json"

/**
 * A Gradle task for preparing searchable options used by the [JarSearchableOptionsTask].
 * The task filters and prepares content from various directories to a specified output directory.
 */
@CacheableTask
abstract class PrepareJarSearchableOptionsTask @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {

    /**
     * Specifies the directory where the prepared searchable options are read from.
     *
     * Default value: [BuildSearchableOptionsTask.outputDirectory]
     */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty

    /**
     * Specifies the directory where the filtered content is placed.
     *
     * Default value: [ProjectLayout.getBuildDirectory]/tmp/prepareJarSearchableOptions
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * Specifies the `lib` directory within the current sandbox.
     *
     * Default value: [PrepareSandboxTask.pluginDirectory]/lib
     */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val libContainer: DirectoryProperty

    /**
     * Specifies the final composed Jar archive with the plugin content.
     *
     * Default value: [ComposedJarTask.archiveFile]
     */
    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val composedJarFile: RegularFileProperty

    @TaskAction
    fun prepareJarSearchableOptions() {
        val pluginId = FileSystems
            .newFileSystem(composedJarFile.asPath, null as ClassLoader?)
            .getPath("META-INF", "plugin.xml")
            .parse { id }

        fileSystemOperations.sync {
            from(inputDirectory)
            into(outputDirectory)
            include {
                when {
                    it.isDirectory -> true
                    it.name.endsWith(pluginId + SEARCHABLE_OPTIONS_SUFFIX_JSON) -> true
                    it.name.endsWith(SEARCHABLE_OPTIONS_SUFFIX_JSON) -> false
                    !it.name.endsWith(SEARCHABLE_OPTIONS_SUFFIX_XML) -> false
                    else -> libContainer.asPath.resolve(it.name.removeSuffix(SEARCHABLE_OPTIONS_SUFFIX_XML)).exists()
                }
            }
            eachFile {
                path = when {
                    name.endsWith(SEARCHABLE_OPTIONS_SUFFIX_JSON) -> name
                    else -> "search/$name"
                }
            }
        }
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Prepares the content used by the jarSearchableOptions task."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<PrepareJarSearchableOptionsTask>(Tasks.PREPARE_JAR_SEARCHABLE_OPTIONS) {
                val buildSearchableOptionsTaskProvider = project.tasks.named<BuildSearchableOptionsTask>(Tasks.BUILD_SEARCHABLE_OPTIONS)
                val buildSearchableOptionsEnabledProvider = project.extensionProvider.flatMap { it.buildSearchableOptions }
                val composedJarTaskProvider = project.tasks.named<ComposedJarTask>(Tasks.COMPOSED_JAR)
                val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)

                inputDirectory.convention(buildSearchableOptionsTaskProvider.flatMap { it.outputDirectory })
                composedJarFile.convention(composedJarTaskProvider.flatMap { it.archiveFile })
                libContainer.convention(prepareSandboxTaskProvider.flatMap { it.pluginDirectory.dir(Sandbox.Plugin.LIB) })
                outputDirectory.convention(
                    project.layout.dir(project.provider {
                        temporaryDir
                    })
                )

                onlyIf {
                    buildSearchableOptionsEnabledProvider.get()
                }
            }
    }
}
