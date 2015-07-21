package org.jetbrains.intellij
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction

class PluginVersionTask extends DefaultTask {
    public static final String NAME = "patchPluginVersion"
    private SourceSet sourceSet

    PluginVersionTask() {
        super()
        name = NAME
        description = "Set plugin version in plugin.xml."
        group = "intellij";
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    @TaskAction
    public def patchPluginXml() {
        sourceSet.output.files.each { file ->
            project.fileTree(file).include("META-INF/plugin.xml").each { File xmlFile ->
                def pluginXml = new XmlParser().parse(xmlFile)
                pluginXml.version.each {
                    it.value = project.version
                }

                def printer = new XmlNodePrinter(new PrintWriter(new FileWriter(xmlFile)))
                printer.preserveWhitespace = true
                printer.print(pluginXml)
            }
        }
    }

    void setSourceSet(SourceSet sourceSet) {
        this.sourceSet = sourceSet
    }
}
