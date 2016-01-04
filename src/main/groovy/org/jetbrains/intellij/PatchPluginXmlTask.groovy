package org.jetbrains.intellij

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.jetbrains.annotations.NotNull

class PatchPluginXmlTask implements Action<Task> {
    @Override
    void execute(Task task) {
        def (since, until) = sinceUntilBuild(task.project)
        Utils.outPluginXmlFiles(task.project).each { file ->
            def pluginXml = Utils.parseXml(file)
            
            if (since != null && until != null) {
                def ideaVersionTag = pluginXml.'idea-version'
                if (ideaVersionTag) {
                    ideaVersionTag*.'@since-build' = since
                    ideaVersionTag*.'@until-build' = until
                } else {
                    pluginXml.children().add(0, new Node(null, 'idea-version', ['since-build': since, 'until-build': until]))
                }
            }

            def versionToSet = task.project.version
            if (versionToSet && versionToSet != Project.DEFAULT_VERSION) {
                def version = pluginXml.version
                if (version) {
                    version*.value = versionToSet
                } else {
                    pluginXml.children().add(0, new Node(null, 'version', versionToSet));
                }
            }
            
            def printer = new XmlNodePrinter(new PrintWriter(new FileWriter(file)))
            printer.preserveWhitespace = true
            printer.print(pluginXml)
        }
    }

    static def sinceUntilBuild(@NotNull Project project) {
        def extension = project.extensions.findByName(IntelliJPlugin.EXTENSION_NAME) as IntelliJPluginExtension
        if (extension != null && extension.updateSinceUntilBuild) {
            try {
                def matcher = Utils.VERSION_PATTERN.matcher(Utils.ideaBuildNumber(extension.ideaDirectory))
                if (matcher.find()) {
                    def since = matcher.group(2)
                    def dotPosition = since.indexOf('.')
                    if (dotPosition > 0) {
                        return new Tuple(since, since.substring(0, dotPosition) + ".9999")
                    }
                }
            } catch (IOException e) {
                IntelliJPlugin.LOG.warn("Cannot read build.txt file", e)
            }
        }
        return new Tuple(null, null)
    }
}