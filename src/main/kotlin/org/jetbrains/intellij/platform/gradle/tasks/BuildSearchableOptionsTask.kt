// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask.Companion.systemPropertyDefault
import org.jetbrains.intellij.platform.gradle.tasks.aware.RunnableIdeAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.parse
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import kotlin.io.path.pathString

/**
 * Builds an index of UI components (searchable options) for the plugin.
 * This task runs a headless IDE instance to collect all the available options provided by the plugin's [Settings](https://plugins.jetbrains.com/docs/intellij/settings.html).
 *
 * If your plugin doesn't implement custom settings, it is recommended to disable it with [IntelliJPlatformExtension.buildSearchableOptions].
 *
 * In the case of running the task for the plugin which has the [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor] configures,
 * a warning will be logged regarding potential issues with running headless IDE for paid plugins.
 * It is possible to mute this warning with [BuildFeature.PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING] build feature flag.
 */
@CacheableTask
abstract class BuildSearchableOptionsTask : JavaExec(), RunnableIdeAware {

    /**
     * The directory to which searchable options will be generated.
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * Emit warning if the task is executed by a paid plugin.
     * Can be disabled with [BuildFeature.PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING].
     */
    @get:Internal
    abstract val showPaidPluginWarning: Property<Boolean>

    private val log = Logger(javaClass)

    /**
     * Executes the task, configures and runs the IDE.
     */
    @TaskAction
    override fun exec() {
        if (showPaidPluginWarning.get()) {
            log.warn(
                """
                Due to IDE limitations, it is impossible to run the IDE in headless mode to collect searchable options for a paid plugin.
                As paid plugins require providing a valid license and presenting a UI dialog, it is impossible to handle such a case, and the task will fail.
                Please consider disabling the task. 
                See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-buildSearchableOptions
                """.trimIndent()
            )
        }

        validateIntelliJPlatformVersion()

        workingDir = platformPath.toFile()
        args = args + listOf("traverseUI", outputDirectory.asPath.pathString, "true")

        super.exec()
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Builds an index of UI components (searchable options) for the plugin."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<BuildSearchableOptionsTask>(Tasks.BUILD_SEARCHABLE_OPTIONS) {
                val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)
                applySandboxFrom(prepareSandboxTaskProvider)

                val buildSearchableOptionsEnabled = project.extensionProvider.flatMap { it.buildSearchableOptions }

                outputDirectory.convention(
                    project.layout.dir(project.provider {
                        temporaryDir
                    })
                )
                showPaidPluginWarning.convention(
                    BuildFeature.PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING.isEnabled(project.providers).map {
                        it && pluginXml.orNull?.parse { productDescriptor } != null
                    }
                )

                systemPropertyDefault("idea.l10n.keys", "only")

                inputs.property("intellijPlatform.buildSearchableOptions", buildSearchableOptionsEnabled)

                onlyIf {
                    buildSearchableOptionsEnabled.get()
                }
            }
    }
}
