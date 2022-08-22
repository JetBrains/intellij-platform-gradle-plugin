// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.gradle.kotlin.dsl.property
import org.jetbrains.intellij.error
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.warn
import javax.inject.Inject

open class VerifyPluginTask @Inject constructor(
    objectFactory: ObjectFactory,
) : ConventionTask(), VerificationTask {

    /**
     * Specifies whether the build should fail when the verifications performed by this task fail.
     *
     * Default value: `false`
     */
    @Input
    val ignoreFailures = objectFactory.property<Boolean>()

    /**
     * Specifies whether the build should fail when the verifications performed by this task emit warnings.
     *
     * Default value: `true`
     */
    @Input
    val ignoreWarnings = objectFactory.property<Boolean>()

    /**
     * The location of the built plugin file which will be used for verification.
     *
     * Default value: `${prepareSandboxTask.destinationDir}/${prepareSandboxTask.pluginName}``
     */
    @InputDirectory
    val pluginDir: DirectoryProperty = objectFactory.directoryProperty()

    private val context = logCategory()

    override fun getIgnoreFailures(): Boolean = ignoreFailures.get()

    override fun setIgnoreFailures(ignoreFailures: Boolean) {
        this.ignoreFailures.set(ignoreFailures)
    }

    @TaskAction
    fun verifyPlugin() {
        val creationResult = pluginDir.get().let { IdePluginManager.createManager().createPlugin(it.asFile.toPath()) }
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
                || creationResult.unacceptableWarnings.isNotEmpty()
                || !ignoreWarnings.get() && creationResult.warnings.isNotEmpty()
        if (failBuild && !ignoreFailures.get()) {
            throw GradleException("Plugin verification failed.")
        }
    }
}
