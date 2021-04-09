package org.jetbrains.intellij.tasks

import org.gradle.jvm.tasks.Jar
import org.jetbrains.intellij.IntelliJPluginConstants
import java.io.File

@Suppress("UnstableApiUsage")
open class JarSearchableOptionsTask : Jar() {

    init {
        val pluginJarFiles = mutableSetOf<String>()

        from(project.provider {
            include {
                if (it.isDirectory) {
                    true
                } else {
                    val suffix = ".searchableOptions.xml"
                    if (it.name.endsWith(suffix)) {
                        if (pluginJarFiles.isEmpty()) {
                            val prepareSandboxTask =
                                project.tasks.findByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
                            val lib = "${prepareSandboxTask.pluginName.get()}/lib"
                            File(prepareSandboxTask.destinationDir, lib).list()?.let {
                                pluginJarFiles.addAll(it)
                            }
                        }
                    }
                    val jarName = it.name.replace(suffix, "")
                    pluginJarFiles.contains(jarName)
                }
            }
            "${project.buildDir}/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}"
        })

        eachFile { it.path = "search/$name" }
        includeEmptyDirs = false
    }
}
