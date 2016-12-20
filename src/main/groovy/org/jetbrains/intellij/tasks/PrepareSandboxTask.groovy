package org.jetbrains.intellij.tasks

import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.*
import org.gradle.internal.FileUtils
import org.gradle.internal.jvm.Jvm
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.Utils
import org.jetbrains.intellij.dependency.PluginDependency
import org.xml.sax.SAXParseException

@SuppressWarnings("GroovyUnusedDeclaration")
class PrepareSandboxTask extends Sync {
    Object pluginName
    Object pluginJar
    Object configDirectory
    List<Object> librariesToIgnore = []
    List<Object> pluginDependencies = []

    PrepareSandboxTask() {
        configureExternalPlugins()
        configurePlugin()
    }

    @InputFile
    File getPluginJar() {
        pluginJar != null ? project.file(pluginJar) : null
    }

    void setPluginJar(Object pluginJar) {
        this.pluginJar = pluginJar
    }

    void pluginJar(Object pluginJar) {
        this.pluginJar = pluginJar
    }

    @Input
    String getPluginName() {
        def pluginName = Utils.stringInput(pluginName)
        pluginName != null ? FileUtils.toSafeFileName(pluginName) : null
    }

    void setPluginName(Object pluginName) {
        this.pluginName = pluginName
    }

    void pluginName(Object pluginName) {
        this.pluginName = pluginName
    }

    @Input
    File getConfigDirectory() {
        configDirectory != null ? project.file(configDirectory) : null
    }

    void setConfigDirectory(File configDirectory) {
        this.configDirectory = configDirectory
    }

    void configDirectory(File configDirectory) {
        this.configDirectory = configDirectory
    }

    @Input
    @Optional
    Collection<PluginDependency> getPluginDependencies() {
        this.pluginDependencies.collect { it instanceof Closure ? (it as Closure).call() : it }.flatten().findAll {
            it instanceof PluginDependency
        } as Collection<PluginDependency>
    }

    void setPluginDependencies(Object... pluginDependencies) {
        this.pluginDependencies.clear()
        this.pluginDependencies.addAll(pluginDependencies as List)
    }

    void pluginDependencies(Object... pluginDependencies) {
        this.pluginDependencies.addAll(pluginDependencies as List)
    }

    @InputFiles
    @Optional
    FileCollection getLibrariesToIgnore() {
        project.files(librariesToIgnore)
    }

    void setLibrariesToIgnore(Object... librariesToIgnore) {
        this.librariesToIgnore.clear()
        this.librariesToIgnore.addAll(librariesToIgnore as List)
    }

    void librariesToIgnore(Object... librariesToIgnore) {
        this.librariesToIgnore.addAll(librariesToIgnore as List)
    }

    @Override
    protected void copy() {
        disableIdeUpdate()
        super.copy()
    }

    private void configurePlugin() {
        CopySpec plugin = mainSpec.addChild().into { "${getPluginName()}/lib" }
        plugin.from {
            def runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
            def librariesToIgnore = getLibrariesToIgnore().toSet()
            librariesToIgnore.add(Jvm.current().toolsJar)
            def pluginDependencies = getPluginDependencies()
            librariesToIgnore.addAll((pluginDependencies*.jarFiles.flatten() as Collection<File>))
            librariesToIgnore.addAll(pluginDependencies*.classesDirectory)
            librariesToIgnore.addAll(pluginDependencies*.metaInfDirectory)

            def result = [getPluginJar()]
            runtimeConfiguration.getAllDependencies().each {
                if (it instanceof ProjectDependency) {
                    def dependencyProject = it.dependencyProject
                    def intelliJPlugin = dependencyProject.plugins.findPlugin(IntelliJPlugin)
                    if (intelliJPlugin != null) {
                        if (!Utils.sourcePluginXmlFiles(dependencyProject).isEmpty()) {
                            IntelliJPlugin.LOG.debug(":${dependencyProject.name} project is IntelliJ-plugin project and won't be packed into the target distribution")
                            return
                        }
                    }
                }
                result.addAll(runtimeConfiguration.fileCollection(it).filter { !librariesToIgnore.contains(it) })
            }
            result
        }
    }

    private void configureExternalPlugins() {
        def externalPlugins = mainSpec.addChild()
        externalPlugins.from {
            def result = []
            getPluginDependencies().each {
                if (!it.builtin) {
                    def artifact = it.artifact
                    if (artifact.isDirectory()) {
                        externalPlugins.into(artifact.getName()) { it.from(artifact) }
                    } else {
                        result.add(artifact)
                    }
                }
            }
            result
        }
    }

    private void disableIdeUpdate() {
        def optionsDir = new File(getConfigDirectory(), "options")
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
        def writer
        try {
            writer = new PrintWriter(new FileWriter(updatesConfig))
            def printer = new XmlNodePrinter(writer)
            printer.preserveWhitespace = true
            printer.print(parse)
        }
        finally {
            if (writer) {
                writer.close()
            }
        }
    }
}
