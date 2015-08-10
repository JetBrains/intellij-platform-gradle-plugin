package org.jetbrains.intellij

import org.gradle.api.tasks.JavaExec
import org.gradle.internal.jvm.Jvm

class RunIdeaTask extends JavaExec {
    public static String NAME = "runIdea"

    private IntelliJPluginExtension extension

    public RunIdeaTask() {
        name = NAME
        description = "Runs Intellij IDEA with installed plugin."
        group = IntelliJPlugin.GROUP_NAME
        main = "com.intellij.idea.Main"

        extension = project.extensions.findByName(IntelliJPlugin.EXTENSION_NAME) as IntelliJPluginExtension
        enableAssertions = true
        workingDir = project.file("${extension.ideaDirectory}/bin/")

        def toolsJar = Jvm.current().toolsJar
        if (toolsJar != null) classpath += project.files(toolsJar)
        classpath += project.files("${extension.ideaDirectory}/lib/idea_rt.jar",
                "${extension.ideaDirectory}/lib/idea.jar",
                "${extension.ideaDirectory}/lib/bootstrap.jar",
                "${extension.ideaDirectory}/lib/extensions.jar",
                "${extension.ideaDirectory}/lib/util.jar",
                "${extension.ideaDirectory}/lib/openapi.jar",
                "${extension.ideaDirectory}/lib/trove4j.jar",
                "${extension.ideaDirectory}/lib/jdom.jar",
                "${extension.ideaDirectory}/lib/log4j.jar")
        systemProperties = patchSystemProperties()
    }

    def patchSystemProperties() {
        def properties = Utils.getIdeaSystemProperties(project, super.systemProperties, extension, false)
        if (isMac()) {
            properties.put("idea.smooth.progress", false);
            properties.put("apple.laf.useScreenMenuBar", true);
        }
        else if (isUnix() && !properties.containsKey("sun.awt.disablegrab")) {
            properties.put("sun.awt.disablegrab", true);
        }
        properties.put("idea.is.internal", true)
        properties.put("idea.classpath.index.enabled", false)
        return properties
    }

    @Override
    List<String> getJvmArgs() {
        return Utils.getIdeaJvmArgs(this, super.jvmArgs, extension);
    }

    static boolean isMac() {
        return System.getProperty("os.name").toLowerCase(Locale.US).startsWith("mac")
    }

    static boolean isUnix() {
        def osName = System.getProperty("os.name").toLowerCase(Locale.US)
        def isWindows = osName.startsWith("windows")
        def isOS2 = osName.startsWith("os/2") || osName.startsWith("os2")
        return !isWindows && !isOS2
    }
}
