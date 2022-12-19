// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.*
import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import groovy.lang.Closure
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import org.jdom2.Element
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginProjectDependency
import org.jetbrains.intellij.error
import org.jetbrains.intellij.ifNull
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.transformXml
import java.io.File
import java.nio.file.Path

@DisableCachingByDefault(because = "Setting up configuration on local machine")
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
    @get:PathSensitive(PathSensitivity.RELATIVE)
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
    private val usedNames = mutableMapOf<String, Path>()

    @TaskAction
    override fun copy() {
        disableIdeUpdate()
        super.copy()
    }

    fun intoChild(destinationDir: Any) = mainSpec.addChild().into(destinationDir)

    override fun getDestinationDir() = defaultDestinationDir.get()

    override fun configure(closure: Closure<*>): Task = super.configure(closure)

    fun configureCompositePlugin(pluginDependency: PluginProjectDependency) {
        from(pluginDependency.artifact) {
            into(pluginDependency.artifact.name)
        }
    }

    fun configureExternalPlugin(pluginDependency: PluginDependency) {
        if (pluginDependency.builtin) {
            return
        }
        pluginDependency.artifact.run {
            if (isDirectory) {
                from(this) {
                    into(name)
                }
            } else {
                from(this)
            }
        }
    }

    private fun disableIdeUpdate() {
        val optionsDir = Path.of(configDir.get())
            .resolve("options")
            .takeIf { it.createDir().exists() }
            .ifNull { error(context, "Cannot disable update checking in host IDE") }
            ?: return

        val updatesConfig = optionsDir.resolve("updates.xml")
            .also {
                if (!it.exists()) {
                    it.create()
                }
            }
            .takeIf(Path::exists)
            .ifNull { error(context, "Cannot disable update checking in host IDE") }
            ?: return

        if (updatesConfig.readText().trim().isEmpty()) {
            updatesConfig.writeText("<application/>")
        }

        updatesConfig.inputStream().use { inputStream ->
            val document = JDOMUtil.loadDocument(inputStream)
            val application = document.rootElement
                .takeIf { it.name == "application" }
                ?: throw GradleException("Invalid content of '$updatesConfig' – '<application>' root element was expected.")

            val updatesConfigurable = application
                .getChildren("component")
                .find { it.getAttributeValue("name") == "UpdatesConfigurable" }
                ?: Element("component")
                    .apply {
                        setAttribute("name", "UpdatesConfigurable")
                        application.addContent(this)
                    }

            val option = updatesConfigurable
                .getChildren("option")
                .find { it.getAttributeValue("name") == "CHECK_NEEDED" }
                ?: Element("option")
                    .apply {
                        setAttribute("name", "CHECK_NEEDED")
                        updatesConfigurable.addContent(this)
                    }

            option.setAttribute("value", "false")
            transformXml(document, updatesConfig)
        }
    }

    fun ensureName(path: Path): String {
        var name = path.simpleName
        var index = 1
        var previousPath = usedNames.putIfAbsent(name, path)
        while (previousPath != null && previousPath != path) {
            name = "${path.nameWithoutExtension}_${index++}.${path.extension}"
            previousPath = usedNames.putIfAbsent(name, path)
        }

        return name
    }
}
