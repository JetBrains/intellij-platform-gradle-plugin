// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIRECTORY
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.isBuildFeatureEnabled
import org.jetbrains.intellij.platform.gradle.tasks.aware.RunnableIdeAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.parsePluginXml
import kotlin.io.path.absolutePathString

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

    init {
        group = PLUGIN_GROUP_NAME
        description = "Builds an index of UI components (searchable options) for the plugin."
    }

    /**
     * Executes the task, configures and runs the IDE.
     */
    @TaskAction
    override fun exec() {
        if (showPaidPluginWarning.get()) {
            log.warn("""
                Due to IDE limitations, it is impossible to run the IDE in headless mode to collect searchable options for a paid plugin.
                As paid plugins require providing a valid license and presenting a UI dialog, it is impossible to handle such a case, and the task will fail.
                Please consider disabling the task in the Gradle configuration. 
                See: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-option
            """.trimIndent()
            )
        }

        assertIntelliJPlatformSupportedVersion()

        workingDir = platformPath.toFile()
        args = args + listOf("traverseUI", outputDirectory.asPath.absolutePathString(), "true")

        super.exec()
    }

    override fun getExecutable() = runtimeExecutable.asPath.absolutePathString()

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<BuildSearchableOptionsTask>(Tasks.BUILD_SEARCHABLE_OPTIONS) {
                val patchPluginXmlTaskProvider = project.tasks.named<PatchPluginXmlTask>(Tasks.PATCH_PLUGIN_XML)
                val pluginXmlProvider = patchPluginXmlTaskProvider.flatMap { it.outputFile }
                val extension = project.the<IntelliJPlatformExtension>()

                outputDirectory.convention(
                    project.layout.buildDirectory.dir(SEARCHABLE_OPTIONS_DIRECTORY)
                )
                showPaidPluginWarning.convention(
                    project.isBuildFeatureEnabled(BuildFeature.PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING).zip(pluginXmlProvider) { enabled, file ->
                        enabled && parsePluginXml(file.asPath)?.productDescriptor != null
                    }
                )

                dependsOn(patchPluginXmlTaskProvider)

                inputs.property("intellijPlatform.buildSearchableOptions", extension.buildSearchableOptions)

                onlyIf {
                    extension.buildSearchableOptions.get()
                }
            }
    }
}
