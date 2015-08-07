package org.jetbrains.intellij
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class PluginVersionTask extends DefaultTask {
    public static final String NAME = "patchPluginVersion"

    PluginVersionTask() {
        name = NAME
        description = "Set plugin version in plugin.xml."
        group = IntelliJPlugin.GROUP_NAME
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    @TaskAction
    public def patchPluginXml() {
        Utils.outPluginXmlFiles(project).each { file ->
            def pluginXml = new XmlParser().parse(file)
            pluginXml.version.each {
                it.value = project.version
            }

            def printer = new XmlNodePrinter(new PrintWriter(new FileWriter(file)))
            printer.preserveWhitespace = true
            printer.print(pluginXml)
        }
    }
}
