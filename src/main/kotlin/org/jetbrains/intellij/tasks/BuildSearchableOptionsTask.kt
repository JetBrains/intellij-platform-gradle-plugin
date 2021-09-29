package org.jetbrains.intellij.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.jetbrains.intellij.IntelliJPluginConstants

@CacheableTask
open class BuildSearchableOptionsTask : RunIdeBase(false) {

    @OutputDirectory
    val outputDir: DirectoryProperty = objectFactory.directoryProperty()

    private val traverseUIArgs = listOf("traverseUI")

    init {
        args = traverseUIArgs
    }

    override fun exec() {
        args = (args ?: emptyList()) + listOf("${outputDir.get()}/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}", "true")
        super.exec()
    }

    override fun setArgs(applicationArgs: List<String>?): JavaExec =
        super.setArgs(traverseUIArgs.union(applicationArgs?.toList() ?: emptyList()))

    override fun setArgs(applicationArgs: MutableIterable<*>?): JavaExec =
        super.setArgs(traverseUIArgs.union(applicationArgs?.toList() ?: emptyList()))
}
