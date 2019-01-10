package org.jetbrains.intellij.tasks

import org.gradle.api.file.FileTreeElement
import org.gradle.jvm.tasks.Jar
import org.jetbrains.intellij.IntelliJPlugin

class JarSearchableOptionsTask extends Jar {
    JarSearchableOptionsTask() {
        def pluginJarFiles = null
        from {
            include { FileTreeElement element ->
                if (element.directory) {
                    return true
                }
                def suffix = ".searchableOptions.xml"
                if (element.name.endsWith(suffix)) {
                    if (pluginJarFiles == null) {
                        def prepareSandboxTask = project.tasks.findByName(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
                        def lib = "${prepareSandboxTask.getPluginName()}/lib"
                        def files = new File(prepareSandboxTask.getDestinationDir(), lib).list()
                        pluginJarFiles = files != null ? files as Set : []
                    }
                    def jarName = element.name.replace(suffix, "")
                    pluginJarFiles.contains(jarName)
                }
            }
            "$project.buildDir/$IntelliJPlugin.SEARCHABLE_OPTIONS_DIR_NAME"
        }
        eachFile { path = "search/$name" }
        includeEmptyDirs = false
    }
}
