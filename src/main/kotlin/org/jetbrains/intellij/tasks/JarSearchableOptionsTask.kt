package org.jetbrains.intellij.tasks

import org.gradle.jvm.tasks.Jar
import org.jetbrains.intellij.IntelliJPluginConstants
import java.io.File

@Suppress("UnstableApiUsage")
open class JarSearchableOptionsTask : Jar() {

    private val buildDir = project.buildDir.canonicalPath

    init {
        val pluginJarFiles = mutableSetOf<String>()

        this.from({
            include {
                when {
                    it.isDirectory -> true
                    else -> {
                        val suffix = ".searchableOptions.xml"
                        if (it.name.endsWith(suffix) && pluginJarFiles.isEmpty()) {
                            val prepareSandboxTask =
                                project.tasks.findByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
                            val lib = "${prepareSandboxTask.pluginName.get()}/lib"
                            File(prepareSandboxTask.destinationDir, lib).list()?.let { files ->
                                pluginJarFiles.addAll(files)
                            }
                        }
                        val jarName = it.name.replace(suffix, "")
                        pluginJarFiles.contains(jarName)
                    }
                }
            }
            "$buildDir/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}"
        })

        this.eachFile { it.path = "search/${it.name}" }
        includeEmptyDirs = false
    }
}
