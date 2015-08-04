package org.jetbrains.intellij

import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.JavaExec

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
        classpath = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME) - 
                project.files(extension.intellijFiles) + project.files(extension.runClasspath)
    }
    
    @Override
    List<String> getJvmArgs() {
        def result = []
        result.addAll(super.jvmArgs)
        result += '-Didea.is.internal=true'
        result += "-Didea.plugins.path=${extension.sandboxDirectory}/plugins"
        result += "-Didea.config.path=${extension.sandboxDirectory}/config"
        result += "-Didea.system.path=${extension.sandboxDirectory}/system"
        result += "-Dfile.encoding=UTF-8" 
        result += "-Xmx512m" 
        result += "-Xms256m" 
        result += "-XX:MaxPermSize=250m" 
        result += "-Didea.classpath.index.enabled=false"
        result += "-Xbootclasspath/a:${extension.ideaDirectory.absolutePath}/lib/boot.jar"
        return result
    }
}
