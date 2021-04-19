package org.jetbrains.intellij.tasks

import com.fasterxml.jackson.core.exc.StreamReadException
import groovy.lang.Closure
import org.gradle.api.Task
import org.gradle.api.file.RegularFileProperty
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
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginProjectDependency
import org.jetbrains.intellij.error
import org.jetbrains.intellij.model.Component
import org.jetbrains.intellij.model.Option
import org.jetbrains.intellij.model.UpdatesConfigurable
import org.jetbrains.intellij.parseXml
import org.jetbrains.intellij.writeXml
import java.io.File

@Suppress("UnstableApiUsage")
open class PrepareSandboxTask : Sync() {

    @Input
    val pluginName: Property<String> = project.objects.property(String::class.java)

    @Input
    val configDirectory: Property<String> = project.objects.property(String::class.java)

    @InputFile
    val pluginJar: RegularFileProperty = project.objects.fileProperty()

    @InputFiles
    @Optional
    val librariesToIgnore: ListProperty<File> = project.objects.listProperty(File::class.java)

    @Input
    @Optional
    val pluginDependencies: ListProperty<PluginDependency> = project.objects.listProperty(PluginDependency::class.java)

    @Internal
    fun getPluginJarFromSandbox() = File(destinationDir, "${pluginName.get()}/lib/${pluginJar.get().asFile.name}")

    override fun configure(closure: Closure<*>): Task {
        return super.configure(closure)
    }

    init {
        configurePlugin()
    }

    private fun configurePlugin() {
        val plugin = mainSpec.addChild().into(project.provider { "${pluginName.get()}/lib" })
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
        })
    }

    @TaskAction
    override fun copy() {
        disableIdeUpdate()
        super.copy()
    }

    fun configureCompositePlugin(pluginDependency: PluginProjectDependency) {
        from(pluginDependency.artifact) { it.into(pluginDependency.artifact.name) }
    }

    fun configureExternalPlugin(pluginDependency: PluginDependency) {
        if (!pluginDependency.builtin) {
            val artifact = pluginDependency.artifact
            if (artifact.isDirectory) {
                from(artifact) { it.into(artifact.name) }
            } else {
                from(artifact)
            }
        }
    }

    private fun disableIdeUpdate() {
        val optionsDir = File(configDirectory.get(), "/options").apply {
            if (!exists() && !mkdirs()) {
                error(this, "Cannot disable update checking in host IDE")
                return
            }
        }

        val updatesConfig = File(optionsDir, "updates.xml").apply {
            if (!exists() && !createNewFile()) {
                error(this, "Cannot disable update checking in host IDE")
                return
            }
        }

        val updatesConfigurable = try {
            parseXml(updatesConfig, UpdatesConfigurable::class.java)
        } catch (ignore: StreamReadException) { // TODO: SAXParseException?
            UpdatesConfigurable()
        }

        val component = updatesConfigurable.items.find { it.name == "UpdatesConfigurable" }
            ?: Component(name = "UpdatesConfigurable").also { updatesConfigurable.items.add(it) }

        val option = component.options.find { it.name == "CHECK_NEEDED" }
            ?: Option("CHECK_NEEDED", false).also { component.options.add(it) }

        option.value = false

        writeXml(updatesConfig, updatesConfigurable)
    }
}
