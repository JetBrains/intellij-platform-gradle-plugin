package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.create
import com.jetbrains.plugin.structure.base.utils.createDir
import com.jetbrains.plugin.structure.base.utils.exists
import com.jetbrains.plugin.structure.base.utils.inputStream
import com.jetbrains.plugin.structure.base.utils.readText
import com.jetbrains.plugin.structure.base.utils.writeText
import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import groovy.lang.Closure
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.jvm.Jvm
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.jdom2.Element
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginProjectDependency
import org.jetbrains.intellij.error
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.transformXml
import java.io.File
import java.nio.file.Path
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class PrepareSandboxTask @Inject constructor(
    objectFactory: ObjectFactory,
) : Sync() {

    @Input
    val pluginName = objectFactory.property<String>()

    @Input
    val configDir = objectFactory.property<String>()

    @InputFile
    val pluginJar: RegularFileProperty = objectFactory.fileProperty()

    @Input
    @Optional
    val librariesToIgnore = objectFactory.listProperty<File>()

    @Input
    @Optional
    val pluginDependencies = objectFactory.listProperty<PluginDependency>()

    @Internal
    val defaultDestinationDir = objectFactory.property<File>()

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
        val pluginDirectories = pluginDependencies.get().map { it.artifact.absolutePath }

        plugin.from(project.provider {
            listOf(pluginJar.get().asFile) + runtimeConfiguration.allDependencies.map {
                runtimeConfiguration.fileCollection(it).filter { file ->
                    !(librariesToIgnore.contains(file) || pluginDirectories.any { p ->
                        file.absolutePath == p || file.absolutePath.startsWith("$p${File.separator}")
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
            var previousPath = usedNames.putIfAbsent(name, file.absolutePath)
            while (previousPath != null && previousPath != file.absolutePath) {
                name = "${originalName}_${index++}${originalExtension}"
                previousPath = usedNames.putIfAbsent(name, file.absolutePath)
            }
        }
    }

    fun configureCompositePlugin(pluginDependency: PluginProjectDependency) {
        from(pluginDependency.artifact) { into(pluginDependency.artifact.name) }
    }

    fun configureExternalPlugin(pluginDependency: PluginDependency) {
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
        val optionsDir = Path.of(configDir.get()).resolve("options")
            .runCatching { createDir() }
            .onFailure {
                error(context, "Cannot disable update checking in host IDE", it)
                return
            }
            .getOrThrow()

        val updatesConfig = optionsDir.resolve("updates.xml")
            .runCatching {
                when {
                    !exists() -> create()
                    else -> this
                }
            }
            .onFailure {
                when (it) {
                    is FileAlreadyExistsException -> return@onFailure
                    else -> {
                        error(context, "Cannot disable update checking in host IDE", it)
                        return
                    }
                }
            }
            .getOrThrow()

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
