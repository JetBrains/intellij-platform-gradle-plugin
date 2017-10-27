package org.jetbrains.intellij.tasks

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.*
import org.jetbrains.intellij.Utils

@SuppressWarnings("GroovyUnusedDeclaration")
class PatchPluginXmlTask extends ConventionTask {
    private Object destinationDir
    private List<Object> pluginXmlFiles = []
    private Object pluginDescription
    private Object version
    private Object sinceBuild
    private Object untilBuild
    private Object changeNotes
    private Object pluginId

    private static final String TAG_DATA = 'value'

    @OutputDirectory
    File getDestinationDir() {
        destinationDir != null ? project.file(destinationDir) : null
    }

    void setDestinationDir(Object destinationDir) {
        this.destinationDir = destinationDir
    }

    void destinationDir(Object destinationDir) {
        this.destinationDir = destinationDir
    }

    @SkipWhenEmpty
    @InputFiles
    FileCollection getPluginXmlFiles() {
        project.files(pluginXmlFiles)
    }

    void setPluginXmlFiles(Object... pluginXmlFiles) {
        this.pluginXmlFiles.clear()
        this.pluginXmlFiles.addAll(pluginXmlFiles as List)
    }

    void pluginXmlFiles(Object... pluginXmlFiles) {
        this.pluginXmlFiles.addAll(pluginXmlFiles as List)
    }

    @Input
    @Optional
    String getVersion() {
        Utils.stringInput(version)
    }

    void setVersion(Object version) {
        this.version = version
    }

    void version(Object version) {
        this.version = version
    }

    @Input
    @Optional
    String getPluginDescription() {
        Utils.stringInput(pluginDescription)
    }

    void setPluginDescription(Object pluginDescription) {
        this.pluginDescription = pluginDescription
    }

    void pluginDescription(Object pluginDescription) {
        this.pluginDescription = pluginDescription
    }

    @Input
    @Optional
    String getSinceBuild() {
        Utils.stringInput(sinceBuild)
    }

    void setSinceBuild(Object sinceBuild) {
        this.sinceBuild = sinceBuild
    }

    void sinceBuild(Object sinceBuild) {
        this.sinceBuild = sinceBuild
    }

    @Input
    @Optional
    String getUntilBuild() {
        Utils.stringInput(untilBuild)
    }

    void setUntilBuild(Object untilBuild) {
        this.untilBuild = untilBuild
    }

    void untilBuild(Object untilBuild) {
        this.untilBuild = untilBuild
    }

    @Input
    @Optional
    String getChangeNotes() {
        Utils.stringInput(changeNotes)
    }

    void setChangeNotes(Object changeNotes) {
        this.changeNotes = changeNotes
    }

    void changeNotes(Object changeNotes) {
        this.changeNotes = changeNotes
    }

    @Input
    @Optional
    String getPluginId() {
        Utils.stringInput(pluginId)
    }

    void setPluginId(Object pluginId) {
        this.pluginId = pluginId
    }

    void pluginId(Object pluginId) {
        this.pluginId = pluginId
    }

    @TaskAction
    void patchPluginXmlFiles() {
        def files = getPluginXmlFiles()
        files.each { file ->
            def pluginXml = Utils.parseXml(file)

            patchTag(pluginXml, "idea-version", null, [
                    "since-build": getSinceBuild(),
                    "until-build": getUntilBuild()])
            patchTag(pluginXml, "description", getPluginDescription())
            patchTag(pluginXml, "change-notes", getChangeNotes())
            patchTagIf(getVersion() != Project.DEFAULT_VERSION,
                    pluginXml, "version", getVersion())
            patchTag(pluginXml, "id", getPluginId())

            def writer
            try {
                writer = new PrintWriter(new FileWriter(new File(getDestinationDir(), file.getName())))
                def printer = new XmlNodePrinter(writer)
                printer.preserveWhitespace = true
                printer.print(pluginXml)
            }
            finally {
                if (writer) {
                    writer.close()
                }
            }
        }
    }

    static void patchTagIf(boolean shouldPatch, Node pluginXml, String name, String content = null, Map<String, Object> attributes = [:]) {
        if (shouldPatch) patchTag(pluginXml, name, content, attributes)
    }

    static void patchTag(Node pluginXml, String name, String content = null, Map<String, Object> attributes = [:]) {
        if (!name || !pluginXml) return
        def tagNodes = content != null ? [value: content] : [:]
        for (attribute in attributes) {
            tagNodes += [("@$attribute.key" as String) : attribute.value]
        }
        tagNodes.each {
            if (!it.value) return
            def tag = pluginXml."$name"
            if (tag) {
                tag*."$it.key" = it.value
            } else {
                def value = it.key.startsWith("@") ? [(it.key[1..-1]) : it.value] : it.value
                pluginXml.children().add(0, new Node(null, name, value))
            }
        }
    }
}