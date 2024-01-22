// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import groovy.lang.Closure
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.*
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.jdom2.Element
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.model.transformXml
import org.jetbrains.intellij.platform.gradle.tasks.aware.SandboxAware
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Prepares sandbox directory with installed plugin and its dependencies.
 */
@Deprecated(message = "CHECK")
@CacheableTask
abstract class PrepareSandboxTask : Sync(), SandboxAware {

    /**
     * The name of the plugin.
     *
     * Default value: [IntelliJPluginExtension.pluginName]
     */
    @get:Input
    abstract val pluginName: Property<String>

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
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val librariesToIgnore: ConfigurableFileCollection

    /**
     * List of dependencies on external plugins.
     */
    @get:InputFiles
    @get:Classpath
    abstract val pluginsClasspath: ConfigurableFileCollection

    /**
     * Default sandbox destination directory.
     */
    @get:Internal
    abstract val defaultDestinationDir: DirectoryProperty

    @get:InputFiles
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

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

    fun intoChild(destinationDir: Any) = mainSpec.addChild().into(destinationDir)

    override fun getDestinationDir() = defaultDestinationDir.asFile.get()

    override fun configure(closure: Closure<*>) = super.configure(closure)

//    fun configureCompositePlugin(pluginDependency: PluginProjectDependency) {
//        from(pluginDependency.artifact) {
//            into(pluginDependency.artifact.name)
//        }
//    }

//    fun configureExternalPlugin(pluginDependency: PluginDependency) {
//        if (pluginDependency.builtin) {
//            return
//        }
//        pluginDependency.artifact.run {
//            if (isDirectory) {
//                from(this) {
//                    into(name)
//                }
//            } else {
//                from(this)
//            }
//        }
//    }

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

//            val downloadPluginTaskProvider = project.tasks.named<DownloadRobotServerPluginTask>(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)
                val runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                val intellijPlatformPluginsConfiguration = project.configurations.getByName(Configurations.INTELLIJ_PLATFORM_PLUGINS_EXTRACTED)
//                val instrumentedJarTaskProvider = project.tasks.named<Jar>(Tasks.INSTRUMENTED_JAR)
                val jarTaskProvider = project.tasks.named<Jar>(JavaPlugin.JAR_TASK_NAME)
                val extension = project.the<IntelliJPlatformExtension>()

//            val ideaDependencyJarFiles = ideaDependencyProvider.map {
//                project.files(it.jarFiles)
//            }
                val pluginJarProvider = extension.instrumentCode.flatMap {
                    when (it) {
                        true -> project.tasks.named<Jar>(Tasks.INSTRUMENTED_JAR)
                        false -> jarTaskProvider
                    }
                }.flatMap { it.archiveFile }

//            project.tasks.register<PrepareSandboxTask>() {
//                PREPARE_UI_TESTING_SANDBOX
//                from(downloadPluginTaskProvider.flatMap { downloadPluginTask ->
//                    downloadPluginTask.outputDir
//                })
//
//                dependsOn(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)
//            }

                pluginName.convention(extension.pluginConfiguration.name)
                pluginJar.convention(pluginJarProvider)
                defaultDestinationDir.convention(sandboxPluginsDirectory)
//                librariesToIgnore.convention(ideaDependencyJarFiles)
                pluginsClasspath.from(intellijPlatformPluginsConfiguration)
                runtimeClasspath.from(runtimeConfiguration)

                intoChild(pluginName.map { "$it/lib" })
//                    .from(runtimeClasspath.filter { file ->
//                        val librariesToIgnore = librariesToIgnore.get().toSet() + Jvm.current().toolsJar
//                        val pluginDirectories = pluginDependencies.files
//
//                        !(librariesToIgnore.contains(file) || pluginDirectories.any { p ->
//                            file.toPath() == p || file.canonicalPath.startsWith("$p${File.separator}")
//                        })
//                    })
                    .from(runtimeClasspath)
                    .from(pluginJar)
                    .eachFile {
                        name = ensureName(file.toPath())
                    }

                from(pluginsClasspath)

                dependsOn(intellijPlatformPluginsConfiguration)
                dependsOn(jarTaskProvider)
//                dependsOn(instrumentedJarTaskProvider)
                dependsOn(runtimeConfiguration)

                inputs.property("intellijPlatform.instrumentCode", extension.instrumentCode)
//                inputs.file(jarTaskProvider.map { it.archiveFile })
//                inputs.file(instrumentedJarTaskProvider.map { it.archiveFile })
                inputs.files(runtimeConfiguration)
//                outputs.dir(defaultDestinationDir)

//            project.afterEvaluate `{
//                extension.plugins.get().filterIsInstance<Project>().forEach { dependency ->
//                    if (dependency.state.executed) {
//                        configureProjectPluginTasksDependency(dependency, this@withType)
//                    } else {
//                        dependency.afterEvaluate {
//                            configureProjectPluginTasksDependency(dependency, this@withType)
//                        }
//                    }
//                }
//            }`
            }
    }
}
