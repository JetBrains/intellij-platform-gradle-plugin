package org.jetbrains.intellij

import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Sync
import org.jetbrains.annotations.NotNull
import org.xml.sax.SAXParseException

class PrepareSandboxTask extends Sync {
    public static String NAME = "prepareSandbox"

    CopySpec classes
    CopySpec libraries
    CopySpec metaInf

    public PrepareSandboxTask() {
        this(NAME, false)
    }

    protected PrepareSandboxTask(String name, boolean inTests) {
        this.name = name
        group = IntelliJPlugin.GROUP_NAME
        description = "Creates a folder containing the plugins to run Intellij IDEA with."

        CopySpecInternal plugin = rootSpec.addChild()
        classes = plugin.addChild().into("classes")
        libraries = plugin.addChild().into("lib")
        metaInf = plugin.addChild().into("META-INF")

        def extension = project.extensions.findByName(IntelliJPlugin.EXTENSION_NAME) as IntelliJPluginExtension
        configureClasses(extension, inTests)
        configureLibraries(extension)
    }

    @Override
    protected void copy() {
        Utils.outPluginXmlFiles(project).each { File xmlFile ->
            metaInf.from(xmlFile)
            classes.exclude("META-INF/" + xmlFile.name)
            def pluginXml = Utils.parseXml(xmlFile)
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
        disableIdeUpdate()
        super.copy()
    }

    private void disableIdeUpdate() {
        def extension = project.extensions.findByName(IntelliJPlugin.EXTENSION_NAME) as IntelliJPluginExtension
        def optionsDir = new File(Utils.configDir(extension, false), "options")
        if (!optionsDir.exists() && !optionsDir.mkdirs()) {
            IntelliJPlugin.LOG.error("Cannot disable update checking in host IDE")
            return
        }

        def updatesConfig = new File(optionsDir, "updates.xml")
        if (!updatesConfig.exists() && !updatesConfig.createNewFile()) {
            IntelliJPlugin.LOG.error("Cannot disable update checking in host IDE")
            return
        }
        def parse
        try {
            parse = Utils.parseXml(updatesConfig)
        }
        catch (SAXParseException ignore) {
            updatesConfig.text = "<application></application>"
            parse = Utils.parseXml(updatesConfig)
        }

        def component = null
        for (Node c : parse.component) {
            if (c.attribute('name') == 'UpdatesConfigurable') {
                component = c
                break
            }
        }
        if (!component) {
            component = new Node(null, 'component', ['name': 'UpdatesConfigurable'])
            parse.append(component)
        }
        def option = null
        for (Node o : component.option) {
            if (o.attribute('name') == 'CHECK_NEEDED') {
                option = o
                break
            }
        }
        if (!option) {
            option = new Node(null, 'option', ['name': 'CHECK_NEEDED'])
            component.append(option)
        }
        option.'@value' = 'false'
        def printer = new XmlNodePrinter(new PrintWriter(new FileWriter(updatesConfig)))
        printer.preserveWhitespace = true
        printer.print(parse)
    }

    private void configureClasses(@NotNull IntelliJPluginExtension extension, boolean inTests) {
        destinationDir = new File(Utils.pluginsDir(extension, inTests), "$extension.pluginName")
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
                        IntelliJPlugin.LOG.info(":${dependencyProject.name} project is IntelliJ-plugin project and won't be packed into the target distribution")
                        return
                    }
                }
            }
            libraries.from(runtimeConfiguration.fileCollection(it).filter { !extension.intellijFiles.contains(it) })
        }
    }
}
