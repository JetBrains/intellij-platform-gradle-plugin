package org.jetbrains.intellij

import org.gradle.api.tasks.JavaExec
import org.gradle.internal.jvm.Jvm
import org.jetbrains.annotations.NotNull

class RunIdeaTask extends JavaExec {
    public static String NAME = "runIdea"
    
    private static final def prefixTable = [IU: null,
                                            IC: 'Idea',
                                            RM: 'Ruby',
                                            PY: 'Python',
                                            PC: 'PyCharmCore',
                                            PE: 'PyCharmEdu',
                                            PS: 'PhpStorm',
                                            WS: 'WebStorm',
                                            OC: 'AppCode',
                                            CL: 'CLion',
                                            DB: '0xDBE',
                                            AI: 'AndroidStudio']
    private IntelliJPluginExtension extension

    public RunIdeaTask() {
        setMain("com.intellij.idea.Main")

        extension = project.extensions.findByName(IntelliJPlugin.EXTENSION_NAME) as IntelliJPluginExtension
        enableAssertions = true
        def ideaDirectory = Utils.ideaSdkDirectory(extension)
        workingDir = project.file("$ideaDirectory/bin/")

        def toolsJar = Jvm.current().toolsJar
        if (toolsJar != null) classpath += project.files(toolsJar)
        classpath += project.files("$ideaDirectory/lib/idea_rt.jar",
                "$ideaDirectory/lib/idea.jar",
                "$ideaDirectory/lib/bootstrap.jar",
                "$ideaDirectory/lib/extensions.jar",
                "$ideaDirectory/lib/util.jar",
                "$ideaDirectory/lib/openapi.jar",
                "$ideaDirectory/lib/trove4j.jar",
                "$ideaDirectory/lib/jdom.jar",
                "$ideaDirectory/lib/log4j.jar")
        systemProperties = patchSystemProperties(ideaDirectory)
    }

    def patchSystemProperties(@NotNull File ideaDirectory) {
        def properties = Utils.getIdeaSystemProperties(project, super.systemProperties, extension, false)
        if (Utils.isMac()) {
            properties.put("idea.smooth.progress", false);
            properties.put("apple.laf.useScreenMenuBar", true);
        } else if (isUnix() && !properties.containsKey("sun.awt.disablegrab")) {
            properties.put("sun.awt.disablegrab", true);
        }
        properties.put("idea.classpath.index.enabled", false)
        properties.put("idea.is.internal", true)

        if (!properties.containsKey('idea.platform.prefix')) {
            def matcher = Utils.VERSION_PATTERN.matcher(Utils.ideaBuildNumber(ideaDirectory))
            if (matcher.find()) {
                def prefix = prefixTable.get(matcher.group(1))
                if (prefix) {
                    properties.put('idea.platform.prefix', prefix)
                }
            }
        }
        return properties
    }

    @Override
    List<String> getJvmArgs() {
        return Utils.getIdeaJvmArgs(this, super.jvmArgs, extension);
    }

    static boolean isUnix() {
        def osName = System.getProperty("os.name").toLowerCase(Locale.US)
        def isWindows = osName.startsWith("windows")
        def isOS2 = osName.startsWith("os/2") || osName.startsWith("os2")
        return !isWindows && !isOS2
    }
}
