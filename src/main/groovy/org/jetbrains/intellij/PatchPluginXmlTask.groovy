package org.jetbrains.intellij

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.*

@SuppressWarnings("GroovyUnusedDeclaration")
class PatchPluginXmlTask extends ConventionTask {
    private Object destinationDir
    private List<Object> pluginXmlFiles = []
    private Object version
    private Object sinceBuild
    private Object untilBuild

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

    @TaskAction
    void patchPluginXmlFiles() {
        def files = getPluginXmlFiles()
        files.each { file ->
            def pluginXml = Utils.parseXml(file)
            patchSinceUntilBuild(getSinceBuild(), getUntilBuild(), pluginXml)
            patchPluginVersion(getVersion(), pluginXml)

            def printer = new XmlNodePrinter(new PrintWriter(new FileWriter(new File(destinationDir, file.getName()))))
            printer.preserveWhitespace = true
            printer.print(pluginXml)
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
}