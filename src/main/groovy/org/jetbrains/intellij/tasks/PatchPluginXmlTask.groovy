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
    private static final String TAG_NAME = "_tag-name_"
    private static final String BREAK_LOGIC = "_break-logic_"

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

            patchNode(pluginXml, [
                    (TAG_NAME) : "idea-version",
                    "@since-build" : getSinceBuild(),
                    "@until-build" : getUntilBuild()])
            patchNode(pluginXml, [
                    (TAG_NAME) : "description",
                    (TAG_DATA) : getPluginDescription()])
            patchNode(pluginXml, [
                    (TAG_NAME) : "change-notes",
                    (TAG_DATA) : getChangeNotes()])
            patchNode(pluginXml, [
                    (TAG_NAME) : "version",
                    (TAG_DATA) : getVersion(),
                    (BREAK_LOGIC) : getVersion() == Project.DEFAULT_VERSION])
            patchNode(pluginXml, [
                    (TAG_NAME) : "id",
                    (TAG_DATA) : getPluginId()])

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

    static void patchNode(Node pluginXml, Map<String, Object> values) {
        def tagName
        if (!values) return
        if (values.remove(BREAK_LOGIC)) return
        if (!(tagName = values.remove(TAG_NAME))) return
        values.each { it ->
            if (!it.value) return
            def tag = pluginXml."$tagName"
            if (tag) {
                tag*."$it.key" = it.value
            } else {
                def value = it.key.startsWith("@") \
                        ? ([(it.key[1..-1]) : it.value])
                        : (it.value)
                pluginXml.children().add(0, new Node(null, tagName, value))
            }
        }
    }
}