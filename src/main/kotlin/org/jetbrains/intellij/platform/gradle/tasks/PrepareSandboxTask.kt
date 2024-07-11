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
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformPluginsExtension
import org.jetbrains.intellij.platform.gradle.models.transformXml
import org.jetbrains.intellij.platform.gradle.tasks.aware.*
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import org.jetbrains.kotlin.gradle.utils.named
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Prepares a sandbox environment with the installed plugin and its dependencies.
 * The sandbox directory is required by tasks that run IDE and tests in isolation from other instances, like when multiple IntelliJ Platforms are used for
 * testing with [RunIdeTask], [TestIdeTask], [TestIdeUiTask], or [TestIdePerformanceTask] tasks.
 *
 * To fully use the sandbox capabilities in a task, make it extend the [SandboxAware] interface.
 *
 * @see SandboxAware
 * @see IntelliJPlatformExtension.sandboxContainer
 * @see Constants.Sandbox
 */
@CacheableTask
abstract class PrepareSandboxTask : Sync(), IntelliJPlatformVersionAware, SandboxStructure, SplitModeAware {

    /**
     * Represents the suffix used i.e., for test-related tasks.
     */
    @get:Internal
    abstract val sandboxSuffix: Property<String>

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
     * This property is controlled with [IntelliJPlatformPluginsExtension].
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

    @get:OutputDirectory
    abstract val pluginDirectory: DirectoryProperty

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
     * Holds a list of names used to generate suffixed ones to avoid collisions.
     */
    private val usedNames = mutableMapOf<String, Path>()

    private val log = Logger(javaClass)

    @TaskAction
    override fun copy() {
        log.info("Preparing sandbox")
        log.info("sandboxConfigDirectory = ${sandboxConfigDirectory.asPath}")
        log.info("sandboxPluginsDirectory = ${sandboxPluginsDirectory.asPath}")
        log.info("sandboxLogDirectory = ${sandboxLogDirectory.asPath}")
        log.info("sandboxSystemDirectory = ${sandboxSystemDirectory.asPath}")

        disableIdeUpdate(sandboxConfigDirectory)
        disabledPlugins(sandboxConfigDirectory)

        sandboxConfigDirectory.asPath.createDirectories()
        sandboxPluginsDirectory.asPath.createDirectories()
        sandboxLogDirectory.asPath.createDirectories()
        sandboxSystemDirectory.asPath.createDirectories()

        if (splitMode.get()) {
            disableIdeUpdate(sandboxConfigFrontendDirectory)
            disabledPlugins(sandboxConfigFrontendDirectory)
            createSplitModeFrontendPropertiesFile()

            sandboxConfigFrontendDirectory.asPath.createDirectories()
            sandboxPluginsFrontendDirectory.asPath.createDirectories()
            sandboxLogFrontendDirectory.asPath.createDirectories()
            sandboxSystemFrontendDirectory.asPath.createDirectories()
        }

        super.copy()
    }

    fun intoChild(destinationDir: Any) = mainSpec.addChild().into(destinationDir)

    override fun getDestinationDir() = defaultDestinationDirectory.asFile.get()

    override fun configure(closure: Closure<*>) = super.configure(closure)

    /**
     * @throws GradleException
     */
    @Throws(GradleException::class)
    private fun disableIdeUpdate(configDirectory: DirectoryProperty) {
        val updatesConfig = configDirectory.asPath
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

    private fun disabledPlugins(configDirectory: DirectoryProperty) {
        configDirectory.asPath
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
        log.info("Preparing sandbox for a Split Mode.")

        val pluginsDirectory = splitModeTarget.flatMap {
            when (it) {
                SplitModeAware.SplitModeTarget.BOTH -> sandboxPluginsDirectory
                else -> sandboxPluginsFrontendDirectory
            }
        }

        splitModeFrontendProperties.asPath.writeText(
            """
            idea.config.path=${sandboxConfigFrontendDirectory.asPath}
            idea.system.path=${sandboxSystemFrontendDirectory.asPath}
            idea.log.path=${sandboxLogFrontendDirectory.asPath}
            idea.plugins.path=${pluginsDirectory.asPath}
            """.trimIndent()
        )
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

    init {
        group = Plugin.GROUP_NAME
        description = "Prepares sandbox directory with installed plugin and its dependencies."
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<PrepareSandboxTask>(
                Tasks.PREPARE_SANDBOX,
                Tasks.PREPARE_TEST_SANDBOX,
                Tasks.PREPARE_TEST_IDE_UI_SANDBOX,
                Tasks.PREPARE_TEST_IDE_PERFORMANCE_SANDBOX,
            ) {
                val runtimeConfiguration = project.configurations[Configurations.External.RUNTIME_CLASSPATH]
                val composedJarTaskProvider = project.tasks.named<ComposedJarTask>(Tasks.COMPOSED_JAR)
                val intellijPlatformPluginModuleConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE]

                sandboxSuffix.convention(
                    name
                        .substringBefore('_')
                        .removePrefix("prepare")
                        .removeSuffix("Sandbox")
                        .replaceFirstChar { it.lowercase() }
                        .let { "-$it" }
                        .trimEnd('-')
                        .plus('_')
                        .plus(name.substringAfter('_', missingDelimiterValue = ""))
                        .trimEnd('_')
                )

                sandboxDirectory.convention(project.extensionProvider.flatMap {
                    it.sandboxContainer.map { container ->
                        container.dir("${productInfo.productCode}-${productInfo.version}")
                    }
                })

                sandboxConfigDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.CONFIG)
                sandboxPluginsDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.PLUGINS)
                sandboxSystemDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.SYSTEM)
                sandboxLogDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.LOG)

                sandboxConfigFrontendDirectory.convention(sandboxConfigDirectory.map { it.dir("frontend") })
                sandboxPluginsFrontendDirectory.convention(sandboxPluginsDirectory.map { it.dir("frontend") })
                sandboxSystemFrontendDirectory.convention(sandboxSystemDirectory.map { it.dir("frontend") })
                sandboxLogFrontendDirectory.convention(sandboxLogDirectory.map { it.dir("frontend") })

                pluginJar.convention(composedJarTaskProvider.flatMap { it.archiveFile })
                defaultDestinationDirectory.convention(splitModeTarget.flatMap {
                    when (it) {
                        SplitModeAware.SplitModeTarget.FRONTEND -> sandboxPluginsFrontendDirectory
                        else -> sandboxPluginsDirectory
                    }
                })
                pluginDirectory.convention(
                    project.extensionProvider.flatMap { extension ->
                        defaultDestinationDirectory.dir(extension.projectName)
                    }
                )
                pluginsClasspath.from(intelliJPlatformPluginConfiguration)
                runtimeClasspath.from(runtimeConfiguration - intellijPlatformPluginModuleConfiguration)

                splitMode.convention(project.extensionProvider.flatMap { it.splitMode })
                splitModeTarget.convention(project.extensionProvider.flatMap { it.splitModeTarget })

                intoChild(project.extensionProvider.flatMap { it.projectName.map { projectName -> "$projectName/lib" } })
                    .from(runtimeClasspath)
                    .from(pluginJar)
                    .eachFile { name = ensureName(file.toPath()) }
                from(pluginsClasspath)

                inputs.property("instrumentCode", project.extensionProvider.flatMap { it.instrumentCode })
                inputs.property("sandboxDirectory", sandboxDirectory.map { it.asPath.pathString })
                inputs.property("sandboxSuffix", sandboxSuffix)
                inputs.files(runtimeConfiguration)

                sandboxConfigDirectory
                sandboxPluginsDirectory
                sandboxLogDirectory
                sandboxSystemDirectory
                splitMode.map { isSplitMode ->
                    when {
                        isSplitMode -> sandboxConfigFrontendDirectory
                        else -> sandboxConfigDirectory
                    }
                }
                splitMode.map { isSplitMode ->
                    when {
                        isSplitMode -> sandboxPluginsFrontendDirectory
                        else -> sandboxPluginsDirectory
                    }
                }
                splitMode.map { isSplitMode ->
                    when {
                        isSplitMode -> sandboxLogFrontendDirectory
                        else -> sandboxLogDirectory
                    }
                }
                splitMode.map { isSplitMode ->
                    when {
                        isSplitMode -> sandboxSystemFrontendDirectory
                        else -> sandboxSystemDirectory
                    }
                }
            }
    }
}
