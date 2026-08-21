// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.*
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.work.DisableCachingByDefault
import org.jdom2.Document
import org.jdom2.Element
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.currentVariant
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformPluginsExtension
import org.jetbrains.intellij.platform.gradle.models.transformXml
import org.jetbrains.intellij.platform.gradle.tasks.aware.*
import org.jetbrains.intellij.platform.gradle.utils.*
import javax.inject.Inject
import kotlin.io.path.*

internal data class CurrentNativeVariantFiles(
    val enabled: Provider<Boolean>,
    val files: ConfigurableFileCollection,
    val pluginJar: ConfigurableFileCollection,
)

internal fun Project.currentNativeVariantFiles(consumerName: String): CurrentNativeVariantFiles {
    val nativeVariantsProvider = extensionProvider.map { it.nativeVariants }
    val currentVariantProvider = providers.currentVariant()
    val nativeVariantsEnabledProvider = nativeVariantsProvider.flatMap { it.enabled }

    val emptyFiles = objects.fileCollection()
    val emptyFilesProvider = provider { emptyFiles }
    val nativeVariantFiles = objects.fileCollection().from(
        nativeVariantsEnabledProvider.flatMap { enabled ->
            when {
                enabled -> currentVariantProvider.flatMap { variant ->
                    nativeVariantsProvider.map { it[variant] }
                }

                else -> emptyFilesProvider
            }
        }
    )

    val emptyPluginJarDirectoryProvider = layout.buildDirectory.dir("tmp/nativeVariants/empty/$consumerName")
    val nativeVariantPluginJar = objects.fileCollection().from(
        nativeVariantsEnabledProvider.flatMap { enabled ->
            when {
                enabled -> currentVariantProvider.flatMap { variant ->
                    tasks
                        .named<PreparePluginVariantTask>(PreparePluginVariantTask.taskName(variant))
                        .flatMap { it.outputDirectory }
                }

                else -> emptyPluginJarDirectoryProvider
            }
        }
    )

    return CurrentNativeVariantFiles(
        enabled = nativeVariantsEnabledProvider,
        files = nativeVariantFiles,
        pluginJar = nativeVariantPluginJar,
    )
}

/**
 * Prepares a sandbox environment with the plugin and its dependencies installed.
 * The sandbox directory is required by tasks that run IDE and tests in isolation from other instances, like when multiple IntelliJ Platforms are used for
 * testing with [RunIdeTask], [TestIdeTask], [TestIdeUiTask], or [TestIdePerformanceTask] tasks.
 * The sandbox directory is created within the container configurable with [IntelliJPlatformExtension.sandboxContainer].
 *
 * Tasks based on the [PrepareSandboxTask] are _sandbox producers_ and can be associated with _sandbox consumers_.
 * To define the consumer task, make it extend from [SandboxAware] and apply the `consumer.applySandboxFrom(producer)` function.
 */
@Suppress("KDocUnresolvedReference")
@DisableCachingByDefault(because = "Not worth caching")
abstract class PrepareSandboxTask : Sync(), IntelliJPlatformVersionAware, SandboxStructure, SplitModeAware, PluginInstallationTargetAware {

    @get:Inject
    internal abstract val fileSystemOperations: FileSystemOperations

    private val nativeVariantEnabled = project.objects.property(Boolean::class.java).convention(false)
    private val nativeVariantFiles = project.objects.fileCollection()
    private val nativeVariantPluginJar = project.objects.fileCollection()

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
     * Dependencies resolved from [Configurations.INTELLIJ_PLATFORM_SANDBOX_RUNTIME_CLASSPATH] or
     * [Configurations.INTELLIJ_PLATFORM_TEST_SANDBOX_RUNTIME_CLASSPATH].
     *
     * The sandbox configurations inherit dependencies from [JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME] and allow dependency exclusions
     * to be applied only to sandbox contents, without changing the project's compile or test classpaths.
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

        preparePluginDirectories()
        super.copy()
        copyNativeVariant()
    }

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

        val document = when {
            updatesConfig.notExists() || updatesConfig.readText().trim().isEmpty() -> Document(Element("application"))
            else -> updatesConfig.inputStream().use(JDOMUtil::loadDocument)
        }
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

    private fun disabledPlugins(configDirectory: DirectoryProperty) {
        configDirectory.asPath
            .resolve("disabled_plugins.txt")
            .writeTextIfChanged(disabledPlugins.get().joinToString(System.lineSeparator()))
    }

    /**
     * Creates a properties file which will be passed to the frontend process when the IDE is started in Split Mode.
     */
    private fun createSplitModeFrontendPropertiesFile() {
        log.info("Preparing sandbox for a Split Mode.")

        val pluginsDirectory = frontendProcessPluginsDirectory().get().asPath

        splitModeFrontendProperties.asPath.writeTextIfChanged(
            """
            idea.config.path=${sandboxConfigFrontendDirectory.asPath.safePathString}
            idea.system.path=${sandboxSystemFrontendDirectory.asPath.safePathString}
            idea.log.path=${sandboxLogFrontendDirectory.asPath.safePathString}
            idea.plugins.path=${pluginsDirectory.safePathString}
            """.trimIndent()
        )
    }

    private fun preparePluginDirectories() {
        val target = effectivePluginInstallationTarget.get()
        when {
            !splitMode.get() -> cleanupFrontendPluginsDirectory()
            target == SplitModeAware.PluginInstallationTarget.BACKEND -> deleteFrontendPluginsDirectory()
            target == SplitModeAware.PluginInstallationTarget.FRONTEND -> cleanupBackendPluginsDirectoryPreservingFrontend()
            else -> cleanupFrontendPluginsDirectory()
        }
    }

    private fun cleanupFrontendPluginsDirectory() {
        sandboxPluginsFrontendDirectory.asPath.let {
            if (it.exists()) {
                it.toFile().deleteRecursively()
            }
            it.createDirectories()
        }
    }

    private fun deleteFrontendPluginsDirectory() {
        sandboxPluginsFrontendDirectory.asPath.let {
            if (it.exists()) {
                it.toFile().deleteRecursively()
            }
        }
    }

    private fun cleanupBackendPluginsDirectoryPreservingFrontend() {
        val backendPluginsDirectory = sandboxPluginsDirectory.asPath
        val frontendPluginsDirectory = sandboxPluginsFrontendDirectory.asPath

        backendPluginsDirectory.createDirectories()
        backendPluginsDirectory.toFile()
            .listFiles()
            ?.filterNot { it.toPath() == frontendPluginsDirectory }
            ?.forEach { it.deleteRecursively() }
    }

    private fun copyNativeVariant() {
        if (!nativeVariantEnabled.get()) {
            return
        }

        val libDirectory = pluginDirectory.dir(Sandbox.Plugin.LIB)

        pluginDirectory.asPath
            .resolve(Sandbox.Plugin.LIB)
            .resolve(pluginJar.asPath.fileName)
            .deleteIfExists()

        fileSystemOperations.copy {
            from(nativeVariantFiles)
            into(pluginDirectory)
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        fileSystemOperations.copy {
            from(nativeVariantPluginJar)
            into(libDirectory)
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Prepares a sandbox environment with the plugin and its dependencies installed."
        duplicatesStrategy = DuplicatesStrategy.WARN
    }

    internal fun includeCurrentNativeVariant() = with(project.currentNativeVariantFiles(name)) {
        nativeVariantEnabled.set(enabled)
        nativeVariantFiles.from(files)
        nativeVariantPluginJar.from(pluginJar)
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX) {
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
                val intellijPlatformPluginComposedModuleConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_COMPOSED_MODULE]
                val intellijPlatformSandboxRuntimeClasspathConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_SANDBOX_RUNTIME_CLASSPATH]
                val intellijPlatformTestSandboxRuntimeClasspathConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM_TEST_SANDBOX_RUNTIME_CLASSPATH]
                val runtimeConfiguration = project.files(testSandbox.map {
                    when (it) {
                        true -> intellijPlatformTestSandboxRuntimeClasspathConfiguration
                        false -> intellijPlatformSandboxRuntimeClasspathConfiguration
                    }
                })

                sandboxDirectory.convention(project.extensionProvider.flatMap {
                    it.sandboxContainer.map { container ->
                        container.dir(project.name).dir("${productInfo.productCode}-${productInfo.version}")
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
                defaultDestinationDirectory.convention(pluginInstallationDirectory())
                pluginName.convention(project.extensionProvider.flatMap { it.projectName })
                pluginDirectory.convention(defaultDestinationDirectory.dir(pluginName))
                pluginsClasspath.from(intelliJPlatformPluginConfiguration)
                runtimeClasspath.from(runtimeConfiguration - intellijPlatformPluginModuleConfiguration - intellijPlatformPluginComposedModuleConfiguration)

                splitMode.convention(project.extensionProvider.flatMap { it.splitMode })
                pluginInstallationTarget.convention(project.extensionProvider.flatMap { it.pluginInstallationTarget })
                splitModeTarget.conventionFrom(pluginInstallationTarget, project.extensionProvider.flatMap { it.splitModeTarget })

                val lib = pluginName.map { "$it/${Sandbox.Plugin.LIB}" }
                val libModules = pluginName.map { "$it/${Sandbox.Plugin.LIB_MODULES}" }
                val nameCollisionHelper = Action<FileCopyDetails> {
                    val originalName = file.toPath().nameWithoutExtension
                    val extension = file.toPath().extension
                    var i = 0

                    while (content.contains(relativePath.pathString)) {
                        name = "${originalName}_${++i}.$extension"
                    }

                    content.add(relativePath.pathString)
                }

                from(runtimeClasspath) {
                    eachFile(nameCollisionHelper)
                    into(lib)
                }

                from(pluginJar) {
                    eachFile(nameCollisionHelper)
                    into(lib)
                }

                from(intellijPlatformPluginModuleConfiguration) {
                    into(libModules)
                }

                from(pluginsClasspath)

                inputs.property("instrumentCode", project.extensionProvider.flatMap { it.instrumentCode })
                inputs.property("nativeVariantEnabled", nativeVariantEnabled)
                inputs.property("sandboxDirectory", sandboxDirectory.map { it.asPath.pathString })
                inputs.property("sandboxSuffix", sandboxSuffix)
                inputs.files(nativeVariantFiles)
                    .withPropertyName("nativeVariantFiles")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                inputs.files(nativeVariantPluginJar)
                    .withPropertyName("nativeVariantPluginJar")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                inputs.files(runtimeConfiguration)

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
                        defaultDestinationDirectory,
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
