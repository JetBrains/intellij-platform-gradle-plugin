// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.problems.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.error
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.logCategory
import org.jetbrains.intellij.platform.gradle.tasks.base.SandboxAware
import org.jetbrains.intellij.platform.gradle.warn

/**
 * Validates completeness and contents of `plugin.xml` descriptors as well as plugin archive structure.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html">Plugin Configuration File</a>
 * TODO: Use Reporting for handling verification report output? See: https://docs.gradle.org/current/dsl/org.gradle.api.reporting.Reporting.html
 */
@CacheableTask
abstract class VerifyPluginTask : DefaultTask(), SandboxAware {

    /**
     * Specifies whether the build should fail when the verifications performed by this task fail.
     *
     * Default value: `false`
     */
    @get:Input
    abstract val ignoreFailures: Property<Boolean>

    /**
     * Specifies whether the build should fail when the verifications performed by this task emit unacceptable warnings.
     *
     * Default value: `false`
     */
    @get:Input
    abstract val ignoreUnacceptableWarnings: Property<Boolean>

    /**
     * Specifies whether the build should fail when the verifications performed by this task emit warnings.
     *
     * Default value: `true`
     */
    @get:Input
    abstract val ignoreWarnings: Property<Boolean>

    /**
     * The location of the built plugin file which will be used for verification.
     *
     * Default value: `${prepareSandboxTask.destinationDir}/${prepareSandboxTask.pluginName}``
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pluginDirectory: DirectoryProperty

    private val context = logCategory()

    init {
        group = PLUGIN_GROUP_NAME
        description = "Validates completeness and contents of `plugin.xml` descriptors as well as plugin archive structure."
    }

    @TaskAction
    fun verifyPlugin() {
        val creationResult = IdePluginManager.createManager().createPlugin(pluginDirectory.asPath)

        when (creationResult) {
            is PluginCreationSuccess -> {
                creationResult.warnings.forEach {
                    warn(context, it.message)
                }
                creationResult.unacceptableWarnings.forEach {
                    error(context, it.message)
                }
            }

            is PluginCreationFail -> creationResult.errorsAndWarnings.forEach {
                if (it.level == PluginProblem.Level.ERROR) {
                    error(context, it.message)
                } else {
                    warn(context, it.message)
                }
            }

            else -> error(context, creationResult.toString())
        }
        val failBuild = creationResult !is PluginCreationSuccess
                || (!ignoreUnacceptableWarnings.get() && creationResult.unacceptableWarnings.isNotEmpty())
                || (!ignoreWarnings.get() && creationResult.warnings.isNotEmpty())
        if (failBuild && !ignoreFailures.get()) {
            throw GradleException("Plugin verification failed.")
        }
    }

    companion object {
        fun register(project: Project) =
            project.registerTask<VerifyPluginTask>(Tasks.VERIFY_PLUGIN) {
                val extension = project.the<IntelliJPlatformExtension>()

                ignoreFailures.convention(false)
                ignoreUnacceptableWarnings.convention(false)
                ignoreWarnings.convention(true)

                pluginDirectory.convention(sandboxPluginsDirectory.flatMap {
                    it.dir(extension.pluginConfiguration.name)
                })
            }
    }
}
