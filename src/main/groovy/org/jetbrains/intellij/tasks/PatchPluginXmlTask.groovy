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
            patchSinceUntilBuild(getSinceBuild(), getUntilBuild(), pluginXml)
            patchNode("description", getPluginDescription(), pluginXml)
            patchNode("change-notes", getChangeNotes(), pluginXml)
            patchPluginVersion(getVersion(), pluginXml)
            patchNode("id", getPluginId(), pluginXml)

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

    static void patchPluginVersion(String pluginVersion, Node pluginXml) {
        if (pluginVersion && pluginVersion != Project.DEFAULT_VERSION) {
            def version = pluginXml.version
            if (version) {
                version*.value = pluginVersion
            } else {
                pluginXml.children().add(0, new Node(null, 'version', pluginVersion))
            }
        }
    }

    static void patchSinceUntilBuild(String sinceBuild, String untilBuild, Node pluginXml) {
        if (sinceBuild && untilBuild) {
            def ideaVersionTag = pluginXml.'idea-version'
            if (ideaVersionTag) {
                ideaVersionTag*.'@since-build' = sinceBuild
                ideaVersionTag*.'@until-build' = untilBuild
            } else {
                pluginXml.children().add(0, new Node(null, 'idea-version',
                        ['since-build': sinceBuild, 'until-build': untilBuild]))
            }
        }
    }

    static void patchNode(String name, String value, Node pluginXml) {
        if (value != null) {
            def tag = pluginXml."$name"
            if(tag) {
                tag*.value = value
            } else {
                pluginXml.children().add(0, new Node(null, name, value))
            }
        }
    }
}