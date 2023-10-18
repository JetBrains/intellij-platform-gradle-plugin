// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks

import com.jetbrains.plugin.structure.base.utils.*
import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import groovy.lang.Closure
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.file.*
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import org.jdom2.Element
import org.jetbrains.intellij.platform.gradleplugin.*
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Sandbox
import org.jetbrains.intellij.platform.gradleplugin.dependency.PluginDependency
import org.jetbrains.intellij.platform.gradleplugin.dependency.PluginProjectDependency
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.notExists

/**
 * Prepares sandbox directory with installed plugin and its dependencies.
 */
@DisableCachingByDefault(because = "Setting up configuration on local machine")
abstract class PrepareSandboxTask : Sync() {

    /**
     * The name of the plugin.
     *
     * Default value: [IntelliJPluginExtension.pluginName]
     */
    @get:Input
    abstract val pluginName: Property<String>

    /**
     * The directory with the plugin configuration.
     *
     * Default value: [IntelliJPluginExtension.sandboxDir]
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sandboxDirectory: DirectoryProperty

    /**
     * The input plugin JAR file used to prepare the sandbox.
     *
     * Default value: `jar` task output
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pluginJar: RegularFileProperty

    /**
     * Libraries that will be ignored when preparing the sandbox.
     * By default, excludes all libraries that are a part of the [SetupDependenciesTask.idea] dependency.
     *
     * Default value: [org.jetbrains.intellij.dependency.IdeaDependency.jarFiles] of [SetupDependenciesTask.idea]
     */
    @get:Input
    @get:Optional
    abstract val librariesToIgnore: ListProperty<File>

    /**
     * List of dependencies on external plugins.
     *
     * Default value: [IntelliJPluginExtension.getPluginDependenciesList]
     */
    @get:Input
    @get:Optional
    abstract val pluginDependencies: ListProperty<PluginDependency>

    /**
     * Default sandbox destination directory.
     */
    @get:Internal
    abstract val defaultDestinationDir: DirectoryProperty

    @get:InputFiles
    @get:Classpath
    abstract val runtimeClasspathFiles: Property<FileCollection>

    /**
     * Represents the suffix used for test-related configuration.
     */
    @get:Internal
    abstract val testSuffix: Property<String>

    private val context = logCategory()
    private val usedNames = mutableMapOf<String, Path>()

    init {
        group = PLUGIN_GROUP_NAME
        description = "Prepares sandbox directory with installed plugin and its dependencies."
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }

    @TaskAction
    override fun copy() {
        disableIdeUpdate()
        super.copy()
    }

    fun intoChild(destinationDir: Any): CopySpec = mainSpec.addChild().into(destinationDir)

    override fun getDestinationDir() = defaultDestinationDir.asFile.get()

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
        val updatesConfig = sandboxDirectory.dir("${Sandbox.CONFIG}/options").asPath
            .createDirectories()
            .resolve("updates.xml")
            .apply {
                if (notExists()) {
                    createFile()
                }
            }

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
