package org.jetbrains.intellij

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.jetbrains.annotations.NotNull

class PatchPluginXmlAction implements Action<Task> {
    private final Map<String, String> myProperties = new HashMap()

    PatchPluginXmlAction(@NotNull Project project) {
        myProperties.version = project.version
        def extension = project.extensions.findByName(IntelliJPlugin.EXTENSION_NAME) as IntelliJPluginExtension
        if (extension != null && extension.updateSinceUntilBuild) {
            try {
                def matcher = Utils.VERSION_PATTERN.matcher(Utils.ideaBuildNumber(extension.ideaDirectory))
                if (matcher.find()) {
                    def since = matcher.group(2)
                    def dotPosition = since.indexOf('.')
                    if (dotPosition > 0) {
                        def until = extension.sameSinceUntilBuild ? since : since.substring(0, dotPosition) + ".9999"
                        myProperties.since = since
                        myProperties.until = until
                    }
                }
            } catch (IOException e) {
                IntelliJPlugin.LOG.warn("Cannot read build.txt file", e)
            }
        }
    }

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