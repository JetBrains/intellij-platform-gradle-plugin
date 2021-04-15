package org.jetbrains.intellij.tasks

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.JavaExec

@CacheableTask
class BuildSearchableOptionsTask : RunIdeBase(false) {

    private val traverseUIArgs = listOf("traverseUI")

    init {
        args = traverseUIArgs
    }

    override fun setArgs(applicationArgs: List<String>?): JavaExec = super.setArgs(traverseUIArgs + applicationArgs)

    override fun setArgs(applicationArgs: MutableIterable<*>?): JavaExec = super.setArgs(traverseUIArgs + applicationArgs)
}
