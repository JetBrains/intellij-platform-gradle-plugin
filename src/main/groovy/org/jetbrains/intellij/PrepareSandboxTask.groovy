package org.jetbrains.intellij

import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Sync
import org.jetbrains.annotations.NotNull

class PrepareSandboxTask extends Sync {
    public static String NAME = "prepareSandbox"

    CopySpec plugin
    CopySpec classes
    CopySpec libraries
    CopySpec metaInf

    public PrepareSandboxTask() {
        name = NAME
        description = "Creates a folder containing the plugins to run Intellij IDEA with."
        group = IntelliJPlugin.GROUP_NAME

        CopySpecInternal plugins = rootSpec.addChild()
        plugins.into("plugins");
        plugin = plugins.addChild();
        classes = plugin.addChild().into("classes")
        libraries = plugin.addChild().into("lib")
        metaInf = plugin.addChild().into("META-INF")
        
        def extension = project.extensions.findByName(IntelliJPlugin.EXTENSION_NAME) as IntelliJPluginExtension
        destinationDir = new File(extension.sandboxDirectory)
        plugin.into(extension.pluginName)

        Utils.mainSourceSet(project).output.files.each { file ->
            project.fileTree(file).include("META-INF/plugin.xml").each { File xmlFile ->
                metaInf.from(xmlFile)
                def pluginXml = new XmlParser().parse(xmlFile)
                pluginXml.depends.each {
                    def configFilePath = it.attribute('config-file')
                    if (configFilePath != null) {
                        def configFile = new File(xmlFile.parentFile, configFilePath)
                        if (configFile.exists()) {
                            metaInf.from(configFile)
                        }
                    }
                }
            }
        }

        classes.from(Utils.mainSourceSet(project).output)
        libraries.from(projectLibraries(project, extension))
    }

    static FileCollection projectLibraries(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).filter {
            !extension.intellijFiles.contains(it)
        }
    }

}
