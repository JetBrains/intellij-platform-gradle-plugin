// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.beans.PluginBean
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask.Companion.systemPropertyDefault
import org.jetbrains.intellij.platform.gradle.tasks.aware.RunnableIdeAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.parse
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import kotlin.io.path.pathString

/**
 * Builds the index of UI components (searchable options) for the plugin.
 * This task runs a headless IDE instance to collect all the available options provided by the plugin's [Settings](https://plugins.jetbrains.com/docs/intellij/settings.html).
 *
 * If the plugin doesn't implement custom settings, it is recommended to disable this task via [IntelliJPlatformExtension.buildSearchableOptions] flag.
 *
 * In the case of running the task for the plugin that has [IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor] defined,
 * a warning will be logged regarding potential issues with running headless IDE for paid plugins.
 * It is possible to mute this warning with the [GradleProperties.PaidPluginSearchableOptionsWarning] Gradle property.
 */
@CacheableTask
abstract class BuildSearchableOptionsTask : JavaExec(), RunnableIdeAware {

    /**
     * Specifies the directory where searchable options will be generated.
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * Emits a warning when the task is executed by a paid plugin.
     * Can be disabled with the [GradleProperties.PaidPluginSearchableOptionsWarning] Gradle property.
     *
     * Default value: [GradleProperties.PaidPluginSearchableOptionsWarning] && [PluginBean.productDescriptor] in [pluginXml] is defined
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
        description = "Builds the index of UI components (searchable options) for the plugin."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<BuildSearchableOptionsTask>(Tasks.BUILD_SEARCHABLE_OPTIONS) {
                val buildSearchableOptionsEnabledProvider = project.extensionProvider.flatMap { it.buildSearchableOptions }
                val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)
                applySandboxFrom(prepareSandboxTaskProvider)

                outputDirectory.convention(
                    project.layout.dir(project.provider {
                        temporaryDir
                    })
                )
                showPaidPluginWarning.convention(
                    project.providers[GradleProperties.PaidPluginSearchableOptionsWarning].map {
                        it && pluginXml.orNull?.parse { productDescriptor } != null
                    }
                )

                systemPropertyDefault("idea.l10n.keys", "only")

                inputs.property("intellijPlatform.buildSearchableOptions", buildSearchableOptionsEnabledProvider)

                onlyIf {
                    buildSearchableOptionsEnabledProvider.get()
                }
            }
    }
}
