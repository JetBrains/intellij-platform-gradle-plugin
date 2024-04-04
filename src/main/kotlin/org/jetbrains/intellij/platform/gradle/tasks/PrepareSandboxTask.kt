// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import groovy.lang.Closure
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.jdom2.Element
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.models.transformXml
import org.jetbrains.intellij.platform.gradle.tasks.aware.SandboxAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SandboxProducerAware
import org.jetbrains.intellij.platform.gradle.utils.asLenient
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Prepares a sandbox environment with the installed plugin and its dependencies.
 * The sandbox directory is required by tasks that run IDE and tests in isolation from other instances, like when multiple IntelliJ Platforms are used for
 * testing with [RunIdeTask], [TestIdeTask], [TestIdeUiTask], or [TestIdePerformanceTask] tasks.
 *
 * To fully utilize the sandbox capabilities in a task, make it extend the [SandboxAware] interface.
 *
 * @see SandboxAware
 * @see IntelliJPlatformExtension.sandboxContainer
 * @see Constants.Sandbox
 */
@CacheableTask
abstract class PrepareSandboxTask : Sync(), SandboxProducerAware {

    /**
     * Default sandbox destination directory to where the plugin files will be copied into.
     *
     * Default value: [SandboxAware.sandboxPluginsDirectory]
     *
     * @see SandboxAware.sandboxPluginsDirectory
     */
    @get:Internal
    abstract val defaultDestinationDirectory: DirectoryProperty

    /**
     * The output of [Jar] task.
     * The proper [Jar.archiveFile] is picked depending on if code instrumentation is enabled.
     *
     * Default value: [Jar.archiveFile]
     *
     * @see JavaPlugin.JAR_TASK_NAME
     * @see Tasks.INSTRUMENTED_JAR
     * @see IntelliJPlatformExtension.instrumentCode
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pluginJar: RegularFileProperty

    /**
     * List of dependencies on external plugins resolved from the [Configurations.INTELLIJ_PLATFORM_PLUGINS_EXTRACTED] configuration.
     *
     * @see Configurations.INTELLIJ_PLATFORM_PLUGINS_EXTRACTED
     * @see IntelliJPlatformDependenciesExtension.plugin
     * @see IntelliJPlatformDependenciesExtension.bundledPlugin
     */
    @get:Classpath
    abstract val pluginsClasspath: ConfigurableFileCollection

    /**
     * Dependencies defined with the [JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME] configuration.
     *
     * @see JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
     */
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    private val usedNames = mutableMapOf<String, Path>()

    init {
        group = Plugin.GROUP_NAME
        description = "Prepares sandbox directory with installed plugin and its dependencies."
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }

    @TaskAction
    override fun copy() {
        disableIdeUpdate()

        super.copy()
    }

    fun intoChild(destinationDir: Any) = mainSpec.addChild().into(destinationDir)

    override fun getDestinationDir() = defaultDestinationDirectory.asFile.get()

    override fun configure(closure: Closure<*>) = super.configure(closure)

    /**
     * @throws GradleException
     */
    @Throws(GradleException::class)
    private fun disableIdeUpdate() {
        val updatesConfig = sandboxConfigDirectory
            .asPath
            .resolve("options")
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
            val application = document.rootElement.takeIf { it.name == "application" }
            requireNotNull(application) { "Invalid content of '$updatesConfig' – '<application>' root element was expected." }

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
        var name = path.name
        var index = 1
        var previousPath = usedNames.putIfAbsent(name, path)
        while (previousPath != null && previousPath != path) {
            name = "${path.nameWithoutExtension}_${index++}.${path.extension}"
            previousPath = usedNames.putIfAbsent(name, path)
        }

        return name
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX, Tasks.PREPARE_TEST_SANDBOX, Tasks.PREPARE_UI_TEST_SANDBOX) {
                val runtimeConfiguration = project.configurations[Configurations.External.RUNTIME_CLASSPATH]
                val intellijPlatformPluginsConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_PLUGINS_EXTRACTED].asLenient
                val instrumentedJarTaskProvider = project.tasks.named<Jar>(Tasks.INSTRUMENTED_JAR)
                val jarTaskProvider = project.tasks.named<Jar>(Tasks.External.JAR)
                val extension = project.the<IntelliJPlatformExtension>()

                val pluginJarProvider = extension.instrumentCode.flatMap {
                    when (it) {
                        true -> instrumentedJarTaskProvider
                        false -> jarTaskProvider
                    }
                }.flatMap { it.archiveFile }

                pluginJar.convention(pluginJarProvider)
                defaultDestinationDirectory.convention(sandboxPluginsDirectory)
                pluginsClasspath.from(intellijPlatformPluginsConfiguration)
                runtimeClasspath.from(runtimeConfiguration)

                intoChild(extension.projectName.map { "$it/lib" })
                    .from(runtimeClasspath)
                    .from(pluginJar)
                    .eachFile { name = ensureName(file.toPath()) }

                from(pluginsClasspath)

                inputs.property("intellijPlatform.instrumentCode", extension.instrumentCode)
                inputs.files(runtimeConfiguration)
            }
    }
}
