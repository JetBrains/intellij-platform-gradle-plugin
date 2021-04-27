package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.jetbrains.intellij.error
import org.jetbrains.intellij.warn
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class VerifyPluginTask @Inject constructor(
    objectFactory: ObjectFactory,
) : ConventionTask(), VerificationTask {

    @Input
    val ignoreFailures: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(false)

    override fun getIgnoreFailures(): Boolean = ignoreFailures.get()

    override fun setIgnoreFailures(ignoreFailures: Boolean) {
        this.ignoreFailures.set(ignoreFailures)
    }

    @Input
    val ignoreWarnings: Property<Boolean> = objectFactory.property(Boolean::class.java).convention(true)

    @InputDirectory
    val pluginDirectory: DirectoryProperty = objectFactory.directoryProperty()

    @TaskAction
    fun verifyPlugin() {
        val creationResult = pluginDirectory.get().let { IdePluginManager.createManager().createPlugin(it.asFile.toPath()) }
        when (creationResult) {
            is PluginCreationSuccess -> creationResult.warnings.forEach {
                warn(this, it.message)
            }
            is PluginCreationFail -> creationResult.errorsAndWarnings.forEach {
                if (it.level == PluginProblem.Level.ERROR) {
                    error(this, it.message)
                } else {
                    warn(this, it.message)
                }
            }
            else -> error(this, creationResult.toString())
        }
        val failBuild = creationResult !is PluginCreationSuccess || !ignoreWarnings.get() && creationResult.warnings.isNotEmpty()
        if (failBuild && !ignoreFailures.get()) {
            throw GradleException("Plugin verification failed.")
        }
    }
}
