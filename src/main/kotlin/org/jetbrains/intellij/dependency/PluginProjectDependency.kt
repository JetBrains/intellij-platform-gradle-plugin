package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.error
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import java.io.File

@CompileStatic
@ToString(includeNames = true, includeFields = true, ignoreNulls = true)
@Suppress("UnstableApiUsage")
class PluginProjectDependency(@Transient val project: Project, val context: String?) : PluginDependency {

    private val pluginDirectory: File by lazy {
        val prepareSandboxTask = project.tasks.findByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        if (prepareSandboxTask is PrepareSandboxTask) {
            File(prepareSandboxTask.destinationDir, prepareSandboxTask.pluginName.get())
        } else {
            throw GradleException("Error accessing PrepareSandboxTask")
        }
    }

    private val pluginDependency: PluginDependencyImpl? by lazy {
        pluginDirectory.takeIf { it.exists() }?.let {
            val creationResult = IdePluginManager.createManager().createPlugin(it.toPath())
            if (creationResult is PluginCreationSuccess) {
                val intellijPlugin = creationResult.plugin
                val pluginId = intellijPlugin.pluginId ?: return@let null
                val pluginVersion = intellijPlugin.pluginVersion ?: return@let null

                val pluginDependency = PluginDependencyImpl(pluginId, pluginVersion, it, builtin = false, maven = false)
                pluginDependency.sinceBuild = intellijPlugin.sinceBuild?.asStringWithoutProductCode()
                pluginDependency.untilBuild = intellijPlugin.untilBuild?.asStringWithoutProductCode()
                pluginDependency
            } else {
                error(context, "Cannot use '$pluginDirectory' as a plugin dependency: $creationResult")
                null
            }
        }
    }

    override val id: String
        get() = pluginDependency?.id ?: "<unknown plugin id>"

    override val version: String
        get() = pluginDependency?.version ?: "<unknown plugin version>"

    override val channel: String?
        get() = pluginDependency?.channel

    override val artifact: File
        get() = pluginDirectory

    override val jarFiles: Collection<File>
        get() = pluginDependency?.jarFiles ?: emptyList()

    override val classesDirectory: File?
        get() = pluginDependency?.classesDirectory

    override val metaInfDirectory: File?
        get() = pluginDependency?.metaInfDirectory

    override val sourcesDirectory: File?
        get() = pluginDependency?.sourcesDirectory

    override val builtin: Boolean
        get() = false

    override val maven: Boolean
        get() = false

    override val notation: PluginDependencyNotation
        get() = PluginDependencyNotation(id, null, null)

    override fun isCompatible(ideVersion: IdeVersion) = true
}
