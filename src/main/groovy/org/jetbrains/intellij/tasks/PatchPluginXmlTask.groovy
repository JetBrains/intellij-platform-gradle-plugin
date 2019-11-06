package org.jetbrains.intellij.tasks

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.*
import org.jetbrains.intellij.Utils

import java.nio.charset.StandardCharsets

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
        getPluginXmlFiles().each { file ->
            def pluginXml = Utils.parseXml(file)
            if (!pluginXml) return

            patchAttribute(pluginXml, "idea-version", "since-build", getSinceBuild())
            patchAttribute(pluginXml, "idea-version", "until-build", getUntilBuild())
            patchTag(pluginXml, "description", getPluginDescription())
            patchTag(pluginXml, "change-notes", getChangeNotes())
            if (getVersion() != Project.DEFAULT_VERSION) {
                patchTag(pluginXml, "version", getVersion())
            }
            patchTag(pluginXml, "id", getPluginId())

            writePatchedPluginXml(pluginXml, new File(getDestinationDir(), file.getName()))
        }
    }

    static void writePatchedPluginXml(Node pluginXml, File outputFile) {
        def writer
        try {
            def binaryOutputStream = new BufferedOutputStream(new FileOutputStream(outputFile))
            writer = new PrintWriter(new OutputStreamWriter(binaryOutputStream, StandardCharsets.UTF_8))

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

    void patchTag(Node pluginXml, String name, String content) {
        if (!content) return
        def tag = pluginXml."$name"
        if (tag) {
            def existingValues = tag*.text() - null
            if (existingValues) {
                Utils.warn(this, "Patching plugin.xml: value of `$name$existingValues` tag will be set to `$content`")
            }
            tag*.value = content
        } else {
            pluginXml.children().add(0, new Node(null, name, content))
        }
    }

    void patchAttribute(Node pluginXml, String tagName, String attributeName, String attributeValue) {
        if (!attributeValue) return
        def tag = pluginXml."$tagName"
        if (tag) {
            def existingValues = tag*."@$attributeName" - null
            if (existingValues) {
                Utils.warn(this, "Patching plugin.xml: attribute `$attributeName=$existingValues` of `$tagName` tag will be set to `$attributeValue`")
            }
            tag*."@$attributeName" = attributeValue
        } else {
            pluginXml.children().add(0, new Node(null, tagName, [(attributeName): attributeValue]))
        }
    }
}