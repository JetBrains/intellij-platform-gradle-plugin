package org.jetbrains.intellij

import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Sync
import org.jetbrains.annotations.NotNull

class PrepareSandboxTask extends Sync {
    public static String NAME = "prepareSandbox"

    CopySpec classes
    CopySpec libraries
    CopySpec metaInf

    public PrepareSandboxTask() {
        name = NAME
        group = IntelliJPlugin.GROUP_NAME
        description = "Creates a folder containing the plugins to run Intellij IDEA with."

        CopySpecInternal plugin = rootSpec.addChild()
        classes = plugin.addChild().into("classes")
        libraries = plugin.addChild().into("lib")
        metaInf = plugin.addChild().into("META-INF")

        def extension = project.extensions.findByName(IntelliJPlugin.EXTENSION_NAME) as IntelliJPluginExtension
        configureClasses(extension)
        configureLibraries(extension)
    }

    @Override
    protected void copy() {
        Utils.outPluginXmlFiles(project).each { File xmlFile ->
            metaInf.from(xmlFile)
            classes.exclude("META-INF/" + xmlFile.name)
            def pluginXml = new XmlParser().parse(xmlFile)
            pluginXml.depends.each {
                def configFilePath = it.attribute('config-file')
                if (configFilePath != null) {
                    def configFile = new File(xmlFile.parentFile, configFilePath)
                    if (configFile.exists()) {
                        metaInf.from(configFile)
                        classes.exclude("META-INF/" + configFilePath)
                    }
                }
            }
        }
        super.copy()
    }

    private void configureClasses(@NotNull IntelliJPluginExtension extension) {
        destinationDir = new File(extension.sandboxDirectory, "plugins/$extension.pluginName")
        classes.from(Utils.mainSourceSet(project).output)
    }

    private void configureLibraries(@NotNull IntelliJPluginExtension extension) {
        def runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
        runtimeConfiguration.getAllDependencies().each {
            if (it instanceof ProjectDependency) {
                def dependencyProject = it.dependencyProject
                def intelliJPlugin = dependencyProject.plugins.findPlugin(IntelliJPlugin)
                if (intelliJPlugin != null) {
                    if (Utils.sourcePluginXmlFiles(dependencyProject)) {
                        // skip other plugin projects
                        return
                    }
                }
            }
            libraries.from(runtimeConfiguration.fileCollection(it).filter { !extension.intellijFiles.contains(it) })
        }
    }
}
