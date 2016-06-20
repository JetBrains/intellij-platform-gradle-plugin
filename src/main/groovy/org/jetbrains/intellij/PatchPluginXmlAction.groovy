package org.jetbrains.intellij

import com.intellij.structure.domain.IdeVersion
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.jetbrains.annotations.NotNull

class PatchPluginXmlAction implements Action<Task> {
    private final Map<String, String> myProperties = new HashMap()

    PatchPluginXmlAction(@NotNull Project project) {
        myProperties.version = project.version?.toString()
        def extension = project.extensions.findByType(IntelliJPluginExtension)
        if (extension != null && extension.updateSinceUntilBuild) {
            def ideVersion = IdeVersion.createIdeVersion(extension.ideaDependency.buildNumber)
            myProperties.since = "$ideVersion.baselineVersion.$ideVersion.build"
            myProperties.until = extension.sameSinceUntilBuild ? "${myProperties.since}.*" : "$ideVersion.baselineVersion.*"
        }
    }

    @Input
    @NotNull
    public Map<String, String> getProperties() {
        return myProperties
    }

    @Override
    public void execute(Task task) {
        Utils.outPluginXmlFiles(task.project).each { file ->
            def pluginXml = Utils.parseXml(file)

            if (myProperties.since && myProperties.until) {
                def ideaVersionTag = pluginXml.'idea-version'
                if (ideaVersionTag) {
                    ideaVersionTag*.'@since-build' = myProperties.since
                    ideaVersionTag*.'@until-build' = myProperties.until
                } else {
                    pluginXml.children().add(0, new Node(null, 'idea-version',
                            ['since-build': myProperties.since, 'until-build': myProperties.until]))
                }
            }

            if (myProperties.version && myProperties.version != Project.DEFAULT_VERSION) {
                def version = pluginXml.version
                if (version) {
                    version*.value = myProperties.version
                } else {
                    pluginXml.children().add(0, new Node(null, 'version', myProperties.version));
                }
            }

            def printer = new XmlNodePrinter(new PrintWriter(new FileWriter(file)))
            printer.preserveWhitespace = true
            printer.print(pluginXml)
        }
    }
}