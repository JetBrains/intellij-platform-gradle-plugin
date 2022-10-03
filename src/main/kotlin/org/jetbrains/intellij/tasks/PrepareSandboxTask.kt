// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import groovy.lang.Closure
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.jdom2.Element
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginProjectDependency
import org.jetbrains.intellij.error
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.transformXml
import java.io.File

abstract class PrepareSandboxTask : Sync() {

    /**
     * The name of the plugin.
     *
     * Default value: [org.jetbrains.intellij.IntelliJPluginExtension.pluginName]
     */
    @get:Input
    abstract val pluginName: Property<String>

    /**
     * The directory with the plugin configuration.
     *
     * Default value: [org.jetbrains.intellij.IntelliJPluginExtension.sandboxDir]/config
     */
    @get:Input
    abstract val configDir: Property<String>

    /**
     * The input plugin JAR file used to prepare the sandbox.
     *
     * Default value: `jar` task output
     */
    @get:InputFile
    abstract val pluginJar: RegularFileProperty

    /**
     * Libraries that will be ignored when preparing the sandbox. By default, excludes all libraries that are a part
     * of the [org.jetbrains.intellij.tasks.SetupDependenciesTask.idea] dependency.
     *
     * Default value: [org.jetbrains.intellij.tasks.SetupDependenciesTask.idea.get().jarFiles]
     */
    @get:Input
    @get:Optional
    abstract val librariesToIgnore: ListProperty<File>

    /**
     * List of dependencies of the current plugin.
     *
     * Default value: [org.jetbrains.intellij.IntelliJPluginExtension.getPluginDependenciesList]
     */
    @get:Input
    @get:Optional
    abstract val pluginDependencies: ListProperty<PluginDependency>

    /**
     * Default sandbox destination directory.
     */
    @get:Internal
    abstract val defaultDestinationDir: Property<File>

    private val context = logCategory()

    init {
        duplicatesStrategy = DuplicatesStrategy.FAIL
        configurePlugin()
    }

    @TaskAction
    override fun copy() {
        disableIdeUpdate()
        super.copy()
    }

    override fun getDestinationDir(): File = defaultDestinationDir.get()

    override fun configure(closure: Closure<*>): Task = super.configure(closure)

    private fun configurePlugin() {
        val plugin = mainSpec.addChild().into(project.provider { "${pluginName.get()}/lib" })
        val usedNames = mutableMapOf<String, String>()
        val runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        val librariesToIgnore = librariesToIgnore.get().toSet() + Jvm.current().toolsJar
        val pluginDirectories = pluginDependencies.get().map { it.artifact.canonicalPath }

        plugin.from(project.provider {
            listOf(pluginJar.get().asFile) + runtimeConfiguration.allDependencies.map {
                runtimeConfiguration.fileCollection(it).filter { file ->
                    !(librariesToIgnore.contains(file) || pluginDirectories.any { p ->
                        file.canonicalPath == p || file.canonicalPath.startsWith("$p${File.separator}")
                    })
                }
            }.flatten()
        }).eachFile {
            val dotIndex = name.lastIndexOf('.')
            val originalName = when {
                dotIndex != -1 -> name.substring(0, dotIndex)
                else -> name
            }
            val originalExtension = when {
                dotIndex != -1 -> name.substring(dotIndex)
                else -> ""
            }
            var index = 1
            var previousPath = usedNames.putIfAbsent(name, file.canonicalPath)
            while (previousPath != null && previousPath != file.canonicalPath) {
                name = "${originalName}_${index++}${originalExtension}"
                previousPath = usedNames.putIfAbsent(name, file.canonicalPath)
            }
        }
    }

    internal fun configureCompositePlugin(pluginDependency: PluginProjectDependency) {
        from(pluginDependency.artifact) { into(pluginDependency.artifact.name) }
    }

    internal fun configureExternalPlugin(pluginDependency: PluginDependency) {
        if (pluginDependency.builtin) {
            return
        }
        pluginDependency.artifact.run {
            if (isDirectory) {
                from(this) { into(name) }
            } else {
                from(this)
            }
        }
    }

    private fun disableIdeUpdate() {
        val optionsDir = File(configDir.get(), "/options").apply {
            if (!exists() && !mkdirs()) {
                error(context, "Cannot disable update checking in host IDE")
                return
            }
        }

        val updatesConfig = File(optionsDir, "updates.xml").apply {
            if (!exists() && !createNewFile()) {
                error(context, "Cannot disable update checking in host IDE")
                return
            }
        }

        if (updatesConfig.readText().trim().isEmpty()) {
            updatesConfig.writeText("<application/>")
        }

        updatesConfig.inputStream().use { inputStream ->
            val document = JDOMUtil.loadDocument(inputStream)
            val application = document.rootElement.takeIf { it.name == "application" }
                ?: throw GradleException("Invalid content of '$updatesConfig' – '<application>' root element was expected.")

            val updatesConfigurable = application.getChildren("component").find {
                it.getAttributeValue("name") == "UpdatesConfigurable"
            } ?: Element("component").apply {
                setAttribute("name", "UpdatesConfigurable")
                application.addContent(this)
            }

            val option = updatesConfigurable.getChildren("option").find {
                it.getAttributeValue("name") == "CHECK_NEEDED"
            } ?: Element("option").apply {
                setAttribute("name", "CHECK_NEEDED")
                updatesConfigurable.addContent(this)
            }

            option.setAttribute("value", "false")

            transformXml(document, updatesConfig)
        }
    }
}
