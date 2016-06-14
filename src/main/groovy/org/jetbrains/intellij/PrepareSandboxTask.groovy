package org.jetbrains.intellij

import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Sync
import org.gradle.internal.jvm.Jvm
import org.jetbrains.annotations.NotNull
import org.xml.sax.SAXParseException

class PrepareSandboxTask extends Sync {
    public static String NAME = "prepareSandbox"

    CopySpec classes
    CopySpec libraries
    CopySpec metaInf
    CopySpec externalPlugins

    public PrepareSandboxTask() {
        this(false)
    }

    protected PrepareSandboxTask(boolean inTests) {
        def extension = project.extensions.getByType(IntelliJPluginExtension)

        CopySpecInternal plugin = rootSpec.addChild()
        externalPlugins = plugin.addChild()
        classes = plugin.addChild().into("$extension.pluginName/classes")
        classes.includeEmptyDirs = false
        libraries = plugin.addChild().into("$extension.pluginName/lib")
        metaInf = plugin.addChild().into("$extension.pluginName/META-INF")

        destinationDir = project.file(Utils.pluginsDir(extension, inTests))
        configureClasses()
        configureLibraries(extension)
        configureExternalPlugins(extension)

        inputs.property('pluginDependencies', extension.pluginDependencies)
    }

    @Override
    protected void copy() {
        Utils.outPluginXmlFiles(project).each { File xmlFile ->
            processIdeaXml(xmlFile.parentFile, xmlFile.name, true)
        }
        disableIdeUpdate()
        super.copy()
    }

    def processIdeaXml(File rootFile, String filePath, boolean processDepends) {
        if (rootFile != null && filePath != null) {
            def configFile = new File(rootFile, filePath)
            if (configFile.exists()) {
                metaInf.from(configFile)
                classes.exclude("META-INF/$filePath")
                def pluginXml = Utils.parseXml(configFile)
                if (processDepends) {
                    pluginXml.depends.each {
                        processIdeaXml(configFile.parentFile, it.attribute('config-file'), false)
                    }
                }
                pluginXml.'xi:include'.each {
                    processIdeaXml(configFile.parentFile, it.attribute('href'), processDepends)
                    it.'xi:fallback'.each {
                        it.'xi:include'.each {
                            processIdeaXml(configFile.parentFile, it.attribute('href'), processDepends)
                        }
                    }
                }
            }
        }
    }

    private void disableIdeUpdate() {
        def extension = project.extensions.getByType(IntelliJPluginExtension)
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

    private void configureClasses() {
        classes.from(Utils.mainSourceSet(project).output)
    }

    private void configureLibraries(@NotNull IntelliJPluginExtension extension) {
        def runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
        def intellijFiles = new HashSet<>(extension.ideaDependency.jarFiles)
        intellijFiles.add(Jvm.current().toolsJar)
        def pluginFiles = (extension.pluginDependencies*.jarFiles.flatten() as Collection<File>) + 
                extension.pluginDependencies*.classesDirectory +
                extension.pluginDependencies*.metaInfDirectory
        runtimeConfiguration.getAllDependencies().each {
            if (it instanceof ProjectDependency) {
                def dependencyProject = it.dependencyProject
                def intelliJPlugin = dependencyProject.plugins.findPlugin(IntelliJPlugin)
                if (intelliJPlugin != null) {
                    if (Utils.sourcePluginXmlFiles(dependencyProject)) {
                        IntelliJPlugin.LOG.debug(":${dependencyProject.name} project is IntelliJ-plugin project and won't be packed into the target distribution")
                        return
                    }
                }
            }
            libraries.from(runtimeConfiguration.fileCollection(it).filter {
                !intellijFiles.contains(it) && !pluginFiles.contains(it)
            })
        }
    }

    private void configureExternalPlugins(@NotNull IntelliJPluginExtension extension) {
        extension.pluginDependencies.each {
            if (!it.builtin) {
                def artifact = it.artifact
                if (artifact.isDirectory()) {
                    externalPlugins.into(artifact.getName()) { it.from(artifact) }
                } else {
                    externalPlugins.from(artifact)
                }
            }
        }
    }
}
