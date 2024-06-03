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
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.get
import org.jdom2.Element
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformPluginsExtension
import org.jetbrains.intellij.platform.gradle.models.transformXml
import org.jetbrains.intellij.platform.gradle.tasks.aware.*
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.SplitModeTarget
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import org.jetbrains.kotlin.gradle.utils.named
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Prepares a sandbox environment with the installed plugin and its dependencies.
 * The sandbox directory is required by tasks that run IDE and tests in isolation from other instances, like when multiple IntelliJ Platforms are used for
 * testing with [RunIdeTask], [CustomTestIdeTask], [TestIdeUiTask], or [TestIdePerformanceTask] tasks.
 *
 * To fully use the sandbox capabilities in a task, make it extend the [SandboxAware] interface.
 *
 * @see SandboxAware
 * @see IntelliJPlatformExtension.sandboxContainer
 * @see Constants.Sandbox
 */
@CacheableTask
abstract class PrepareSandboxTask : Sync(), SandboxProducerAware, SplitModeAware {

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
     * An internal field to hold a list of plugins to be disabled within the current sandbox.
     *
     * This property is controlled with [IntelliJPlatformPluginsExtension] method of [CustomIntelliJPlatformVersionAware].
     */
    @get:Internal
    abstract val disabledPlugins: SetProperty<String>

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
     * List of dependencies on external plugins resolved from the [Configurations.INTELLIJ_PLATFORM_PLUGIN] configuration.
     *
     * @see Configurations.INTELLIJ_PLATFORM_PLUGIN
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

    /**
     * As IntelliJ Platform is capable for running in split mode, each [SplitModeTarget.BACKEND] and [SplitModeTarget.FRONTEND] require separated sandbox.
     * This property helps distinguish which one is currently handled.
     *
     * @see [SplitModeAware]
     */
    @get:Internal
    abstract val splitModeCurrentTarget: Property<SplitModeTarget>

    /**
     * Holds a list of names used to generate suffixed ones to avoid collisions.
     */
    private val usedNames = mutableMapOf<String, Path>()

    init {
        group = Plugin.GROUP_NAME
        description = "Prepares sandbox directory with installed plugin and its dependencies."
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }

    @TaskAction
    override fun copy() {
        createSandboxDirectories()
        disableIdeUpdate()
        disabledPlugins()
        createSplitModeFrontendPropertiesFile()

        if (!splitMode.get() || splitModeTarget.get().includes(splitModeCurrentTarget.get())) {
            super.copy()
        }
    }

    fun intoChild(destinationDir: Any) = mainSpec.addChild().into(destinationDir)

    override fun getDestinationDir() = defaultDestinationDirectory.asFile.get()

    override fun configure(closure: Closure<*>) = super.configure(closure)

    private fun createSandboxDirectories() = listOf(
        sandboxConfigDirectory,
        sandboxPluginsDirectory,
        sandboxLogDirectory,
        sandboxSystemDirectory,
    ).forEach {
        it.asPath.createDirectories()
    }

    /**
     * @throws GradleException
     */
    @Throws(GradleException::class)
    private fun disableIdeUpdate() {
        val updatesConfig = sandboxConfigDirectory.asPath
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

    private fun disabledPlugins() {
        sandboxConfigDirectory.asPath
            .resolve("disabled_plugins.txt")
            .writeLines(disabledPlugins.get())
    }

    /**
     * Creates a properties file which will be passed to the frontend process when the IDE is started in Split Mode.
     */
    private fun createSplitModeFrontendPropertiesFile() {
        if (!splitMode.get()) {
            return
        }

        if (splitModeCurrentTarget.get() == SplitModeTarget.FRONTEND) {
            splitModeFrontendProperties.asPath.writeText(
                """
                idea.config.path=${sandboxConfigDirectory.asPath}
                idea.system.path=${sandboxSystemDirectory.asPath}
                idea.log.path=${sandboxLogDirectory.asPath}
                idea.plugins.path=${sandboxPluginsDirectory.asPath}
                """.trimIndent()
            )
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
                val composedJarTaskProvider = project.tasks.named<ComposedJarTask>(Tasks.COMPOSED_JAR)
                val intellijPlatformPluginModuleConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE]

                pluginJar.convention(composedJarTaskProvider.flatMap { it.archiveFile })
                defaultDestinationDirectory.convention(sandboxPluginsDirectory)
                pluginsClasspath.from(intelliJPlatformPluginConfiguration)
                runtimeClasspath.from(runtimeConfiguration - intellijPlatformPluginModuleConfiguration)

                splitModeTarget.convention(project.extensionProvider.flatMap { it.splitModeTarget })
                splitModeCurrentTarget.convention(SplitModeTarget.BACKEND)

                intoChild(project.extensionProvider.flatMap { it.projectName.map { projectName -> "$projectName/lib" } })
                    .from(runtimeClasspath)
                    .from(pluginJar)
                    .eachFile { name = ensureName(file.toPath()) }
                from(pluginsClasspath)

                inputs.property("intellijPlatform.instrumentCode", project.extensionProvider.flatMap { it.instrumentCode })
                inputs.property("intellijPlatform.sandboxDirectoriesExistence", project.provider {
                    listOf(sandboxConfigDirectory, sandboxPluginsDirectory, sandboxLogDirectory, sandboxSystemDirectory).all {
                        it.asPath.exists()
                    }
                })
                inputs.files(runtimeConfiguration)
            }
    }
}
