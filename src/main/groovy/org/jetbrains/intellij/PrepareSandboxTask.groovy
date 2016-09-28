package org.jetbrains.intellij

import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.*
import org.gradle.internal.FileUtils
import org.gradle.internal.jvm.Jvm
import org.jetbrains.intellij.dependency.PluginDependency
import org.xml.sax.SAXParseException

@SuppressWarnings("GroovyUnusedDeclaration")
class PrepareSandboxTask extends Copy {
    Object pluginName
    Object patchedPluginXmlDirectory
    Object configDirectory
    List<Object> librariesToIgnore = []
    List<Object> pluginDependencies = []

    PrepareSandboxTask() {
        pluginSpec = rootSpec.addChild()
        configureClasses()
        configureMetaInf()
        configureExternalPlugins()
        configureLibraries()
    }

    @InputFiles
    FileCollection getClasses() {
        Utils.mainSourceSet(project).output
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

    @InputDirectory
    @Optional
    File getPatchedPluginXmlDirectory() {
        patchedPluginXmlDirectory != null ? project.file(patchedPluginXmlDirectory) : null
    }

    void setPatchedPluginXmlDirectory(File patchedPluginXmlDirectory) {
        this.patchedPluginXmlDirectory = patchedPluginXmlDirectory
    }

    void patchedPluginXmlDirectory(File patchedPluginXmlDirectory) {
        this.patchedPluginXmlDirectory = patchedPluginXmlDirectory
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

    private CopySpecInternal pluginSpec
    private CopySpec classes
    private CopySpec metaInf

    @Override
    protected void copy() {
        processLinkedXmlFiles()
        disableIdeUpdate()
        super.copy()
    }

    private void configureClasses() {
        classes = pluginSpec.addChild().into { "${getPluginName()}/classes" }
        classes.from { getClasses() }
        classes.includeEmptyDirs = false
    }

    private void configureMetaInf() {
        metaInf = pluginSpec.addChild().into { "${getPluginName()}/META-INF" }
        metaInf.from {
            getPatchedPluginXmlDirectory()?.listFiles()
        }
        metaInf.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)
    }

    private void configureLibraries() {
        CopySpec libraries = pluginSpec.addChild().into { "${getPluginName()}/lib" }
        libraries.from {
            def runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
            def librariesToIgnore = getLibrariesToIgnore().toSet()
            librariesToIgnore.add(Jvm.current().toolsJar)
            def pluginDependencies = getPluginDependencies()
            librariesToIgnore.addAll((pluginDependencies*.jarFiles.flatten() as Collection<File>))
            librariesToIgnore.addAll(pluginDependencies*.classesDirectory)
            librariesToIgnore.addAll(pluginDependencies*.metaInfDirectory)

            def result = []
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
                result.addAll(runtimeConfiguration.fileCollection(it).filter { !librariesToIgnore.contains(it) })
            }
            result
        }
    }

    private void configureExternalPlugins() {
        def externalPlugins = pluginSpec.addChild()
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

    private void processLinkedXmlFiles() {
        for (def xmlFile : Utils.outPluginXmlFiles(project)) {
            processIdeaXml(xmlFile.parentFile, xmlFile.name, true)
        }
    }

    private void processIdeaXml(File rootFile, String filePath, boolean processDepends) {
        if (rootFile != null && filePath != null) {
            def configFile = new File(rootFile, filePath)
            if (configFile.exists()) {
                metaInf.from(configFile)
                classes.exclude("META-INF/$filePath")
                def pluginXml = Utils.parseXml(configFile)
                if (processDepends) {
                    for (def dependsTag : pluginXml.depends) {
                        processIdeaXml(configFile.parentFile, dependsTag.attribute('config-file'), false)
                    }
                }
                for (def includeTag : pluginXml.'xi:include') {
                    processIdeaXml(configFile.parentFile, includeTag.attribute('href'), processDepends)
                    for (def fallbackTag : includeTag.'xi:fallback') {
                        for (def innerIncludeTag : fallbackTag.'xi:include') {
                            processIdeaXml(configFile.parentFile, innerIncludeTag.attribute('href'), processDepends)
                        }
                    }
                }
            }
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
        def printer = new XmlNodePrinter(new PrintWriter(new FileWriter(updatesConfig)))
        printer.preserveWhitespace = true
        printer.print(parse)
    }
}
