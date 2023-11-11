// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.base.RunIdeBase
import kotlin.io.path.pathString

/**
 * Builds an index of UI components (searchable options) for the plugin.
 * This task runs a headless IDE instance to collect all the available options provided by the plugin's [Settings](https://plugins.jetbrains.com/docs/intellij/settings.html).
 * Note, that this is a [RunIdeBase]-based task with predefined arguments and all properties of the [RunIdeBase] task are also applied to [BuildSearchableOptionsTask] tasks.
 *
 * If your plugin doesn't implement custom settings, it is recommended to disable it.
 *
 * @see [RunIdeBase]
 * @see [BuildFeature.NO_SEARCHABLE_OPTIONS_WARNING]
 */
@Deprecated(message = "CHECK")
@CacheableTask
abstract class BuildSearchableOptionsTask : RunIdeTask() {

    init {
        group = PLUGIN_GROUP_NAME
        description = "Builds an index of UI components (searchable options) for the plugin."
    }

    /**
     * The directory where the searchable options will be generated.
     */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /**
     * Emit warning if the task is executed by a paid plugin.
     * Can be disabled with [BuildFeature.PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING].
     */
    @get:Internal
    abstract val showPaidPluginWarning: Property<Boolean>

    private val context = logCategory()

    override fun exec() {
        if (showPaidPluginWarning.get()) {
            warn(
                context,
                "Due to IDE limitations, it is impossible to run the IDE in headless mode to collect searchable options for " +
                        "a paid plugin. As paid plugins require providing a valid license and presenting a UI dialog, it is impossible " +
                        "to handle such a case, and the task will fail. Please consider disabling the task in the Gradle configuration. " +
                        "See: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-option"
            )
        }

        args = args + listOf("traverseUI", outputDir.asPath.pathString, "true")

        super.exec()
    }

    companion object {
        fun register(project: Project) =
            project.registerTask<BuildSearchableOptionsTask>(Tasks.BUILD_SEARCHABLE_OPTIONS) {
                outputDir.convention(project.layout.buildDirectory.dir(IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME))
                showPaidPluginWarning.convention(project.isBuildFeatureEnabled(BuildFeature.PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING).map {
                    it && sourcePluginXmlFiles(project).any { file ->
                        parsePluginXml(file)?.productDescriptor != null
                    }
                })
            }
    }
}
