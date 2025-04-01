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
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.work.DisableCachingByDefault
import org.jdom2.Element
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformPluginsExtension
import org.jetbrains.intellij.platform.gradle.models.transformXml
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SandboxAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SandboxStructure
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import org.jetbrains.kotlin.gradle.utils.named
import kotlin.io.path.*

/**
 * Prepares a sandbox environment with the plugin and its dependencies installed.
 * The sandbox directory is required by tasks that run IDE and tests in isolation from other instances, like when multiple IntelliJ Platforms are used for
 * testing with [RunIdeTask], [TestIdeTask], [TestIdeUiTask], or [TestIdePerformanceTask] tasks.
 * The sandbox directory is created within the container configurable with [IntelliJPlatformExtension.sandboxContainer].
 *
 * Tasks based on the [PrepareSandboxTask] are _sandbox producers_ and can be associated with _sandbox consumers_.
 * To define the consumer task, make it extend from [SandboxAware] and apply the `consumer.applySandboxFrom(producer)` function.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class PrepareSandboxTask : Sync(), IntelliJPlatformVersionAware, SandboxStructure, SplitModeAware {

    /**
     * Represents the suffix used i.e., for test-related or custom tasks.
     *
     * The default suffix is composed of the task [name] (`prepare[X]Sandbox[_Y]`) to the `-[X][Y]` format.
     */
    @get:Internal
    abstract val sandboxSuffix: Property<String>

    /**
     * Specifies the default sandbox destination directory where plugin files will be copied.
     *
     * Default value: [SandboxAware.sandboxPluginsDirectory]
     */
    @get:Internal
    abstract val defaultDestinationDirectory: DirectoryProperty

    /**
     * Specifies the name of the plugin directory in the sandbox.
     *
     * Default value: [IntelliJPlatformExtension.projectName].
     */
    @get:Internal
    abstract val pluginName: Property<String>

    /**
     * Specifies the directory where the plugin artifacts are to be placed.
     *
     * Default value: [defaultDestinationDirectory]/[pluginName]
     */
    @get:OutputDirectory
    abstract val pluginDirectory: DirectoryProperty

    /**
     * An internal field to hold a list of plugins to be disabled within the current sandbox.
     *
     * This property is controlled with [IntelliJPlatformPluginsExtension.disablePlugins].
     */
    @get:Input
    @get:Optional
    abstract val disabledPlugins: SetProperty<String>

    /**
     * Specifies the output of the [Jar] task.
     * The proper [Jar.archiveFile] picked depends on whether code instrumentation is enabled.
     *
     * Default value: [Jar.archiveFile]
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pluginJar: RegularFileProperty

    /**
     * Specifies a list of dependencies on external plugins resolved from the [Configurations.INTELLIJ_PLATFORM_PLUGIN] configuration
     * added with [IntelliJPlatformDependenciesExtension.plugin] and [IntelliJPlatformDependenciesExtension.bundledPlugin].
     */
    @get:Classpath
    abstract val pluginsClasspath: ConfigurableFileCollection

    /**
     * Dependencies defined with the [JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME] configuration.
     */
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    /**
     * Holds a list of paths present in the produced sandbox.
     */
    private val content = mutableSetOf<String>()

    private val log = Logger(javaClass)

    @TaskAction
    override fun copy() {
        log.info("Preparing sandbox")
        log.info("sandboxConfigDirectory = ${sandboxConfigDirectory.asPath}")
        log.info("sandboxPluginsDirectory = ${sandboxPluginsDirectory.asPath}")
        log.info("sandboxLogDirectory = ${sandboxLogDirectory.asPath}")
        log.info("sandboxSystemDirectory = ${sandboxSystemDirectory.asPath}")
        log.info("testSandbox = ${testSandbox.get()}")

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

    init {
        group = Plugin.GROUP_NAME
        description = "Prepares a sandbox environment with the plugin and its dependencies installed."
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<PrepareSandboxTask>(
                Tasks.PREPARE_SANDBOX,
                Tasks.PREPARE_TEST_SANDBOX,
                Tasks.PREPARE_TEST_IDE_PERFORMANCE_SANDBOX,
            ) {
                val composedJarTaskProvider = project.tasks.named<ComposedJarTask>(Tasks.COMPOSED_JAR)

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

                val intellijPlatformPluginModuleConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE]
                val intellijPlatformRuntimeClasspathConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_RUNTIME_CLASSPATH]
                val intellijPlatformTestRuntimeClasspathConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_TEST_RUNTIME_CLASSPATH]
                val runtimeConfiguration = project.files(testSandbox.map {
                    when (it) {
                        true -> intellijPlatformTestRuntimeClasspathConfiguration
                        false -> intellijPlatformRuntimeClasspathConfiguration
                    }
                })

                sandboxDirectory.convention(project.extensionProvider.flatMap {
                    it.sandboxContainer.map { container ->
                        container.dir("${productInfo.productCode}-${productInfo.version}")
                    }
                })

                sandboxConfigDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.CONFIG)
                sandboxPluginsDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.PLUGINS)
                sandboxSystemDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.SYSTEM)
                sandboxLogDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.LOG)
                testSandbox.convention(name.contains("Test"))

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
                pluginName.convention(project.extensionProvider.flatMap { it.projectName })
                pluginDirectory.convention(defaultDestinationDirectory.dir(pluginName))
                pluginsClasspath.from(intelliJPlatformPluginConfiguration)
                runtimeClasspath.from(runtimeConfiguration - intellijPlatformPluginModuleConfiguration)

                splitMode.convention(project.extensionProvider.flatMap { it.splitMode })
                splitModeTarget.convention(project.extensionProvider.flatMap { it.splitModeTarget })

                intoChild(pluginName.map { "$it/${Sandbox.Plugin.LIB}" })
                    .from(runtimeClasspath)
                    .from(pluginJar)
                    .eachFile {
                        val originalName = file.toPath().nameWithoutExtension
                        val extension = file.toPath().extension
                        var i = 0

                        while (content.contains(relativePath.pathString)) {
                            name = "${originalName}_${++i}.$extension"
                        }

                        content.add(relativePath.pathString)
                    }
                from(pluginsClasspath)

                inputs.property("instrumentCode", project.extensionProvider.flatMap { it.instrumentCode })
                inputs.property("sandboxDirectory", sandboxDirectory.map { it.asPath.pathString })
                inputs.property("sandboxSuffix", sandboxSuffix)
                inputs.files(intellijPlatformRuntimeClasspathConfiguration)

                outputs.upToDateWhen {
                    listOf(
                        sandboxConfigDirectory,
                        sandboxPluginsDirectory,
                        sandboxLogDirectory,
                        sandboxSystemDirectory,
                        splitMode.flatMap { isSplitMode ->
                            when {
                                isSplitMode -> sandboxConfigFrontendDirectory
                                else -> sandboxConfigDirectory
                            }
                        },
                        splitMode.flatMap { isSplitMode ->
                            when {
                                isSplitMode -> sandboxPluginsFrontendDirectory
                                else -> sandboxPluginsDirectory
                            }
                        },
                        splitMode.flatMap { isSplitMode ->
                            when {
                                isSplitMode -> sandboxLogFrontendDirectory
                                else -> sandboxLogDirectory
                            }
                        },
                        splitMode.flatMap { isSplitMode ->
                            when {
                                isSplitMode -> sandboxSystemFrontendDirectory
                                else -> sandboxSystemDirectory
                            }
                        },
                    ).all { it.asPath.exists() }
                }
            }
    }
}
