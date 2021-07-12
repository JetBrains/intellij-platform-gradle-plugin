package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import groovy.lang.Closure
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.jvm.Jvm
import org.jdom2.Element
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginProjectDependency
import org.jetbrains.intellij.error
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.transformXml
import java.io.File
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class PrepareSandboxTask @Inject constructor(
    objectFactory: ObjectFactory,
) : Sync() {

    @Input
    val pluginName: Property<String> = objectFactory.property(String::class.java)

    @Input
    val configDir: Property<String> = objectFactory.property(String::class.java)

    @InputFile
    val pluginJar: RegularFileProperty = objectFactory.fileProperty()

    @InputFiles
    @Optional
    val librariesToIgnore: ListProperty<File> = objectFactory.listProperty(File::class.java)

    @Input
    @Optional
    val pluginDependencies: ListProperty<PluginDependency> = objectFactory.listProperty(PluginDependency::class.java)

    @Internal
    val defaultDestinationDir: Property<File> = objectFactory.property(File::class.java)

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

    override fun getDestinationDir(): File = super.getDestinationDir() ?: defaultDestinationDir.get()

    override fun configure(closure: Closure<*>): Task = super.configure(closure)

    private fun configurePlugin() {
        val plugin = mainSpec.addChild().into(project.provider { "${pluginName.get()}/lib" })
        val usedNames = mutableMapOf<String, String>()
        val runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        val librariesToIgnore = librariesToIgnore.get().toSet() + Jvm.current().toolsJar
        val pluginDirectories = pluginDependencies.get().map { it.artifact.absolutePath }

        plugin.from(project.provider {
            listOf(pluginJar.get().asFile) + runtimeConfiguration.allDependencies.map {
                runtimeConfiguration.fileCollection(it).filter { file ->
                    !(librariesToIgnore.contains(file) || pluginDirectories.any { p ->
                        file.absolutePath == p || file.absolutePath.startsWith("$p${File.separator}")
                    })
                }
            }.flatten()
        }).eachFile { details ->
            val dotIndex = details.name.lastIndexOf('.')
            val originalName = when {
                dotIndex != -1 -> details.name.substring(0, dotIndex)
                else -> details.name
            }
            val originalExtension = when {
                dotIndex != -1 -> details.name.substring(dotIndex)
                else -> ""
            }
            var index = 1
            var previousPath = usedNames.putIfAbsent(details.name, details.file.absolutePath)
            while (previousPath != null && previousPath != details.file.absolutePath) {
                details.name = "${originalName}_${index++}${originalExtension}"
                previousPath = usedNames.putIfAbsent(details.name, details.file.absolutePath)
            }
        }
    }

    fun configureCompositePlugin(pluginDependency: PluginProjectDependency) {
        from(pluginDependency.artifact) { it.into(pluginDependency.artifact.name) }
    }

    fun configureExternalPlugin(pluginDependency: PluginDependency) {
        if (pluginDependency.builtin) {
            return
        }
        pluginDependency.artifact.run {
            if (isDirectory) {
                from(this) { copy -> copy.into(name) }
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
