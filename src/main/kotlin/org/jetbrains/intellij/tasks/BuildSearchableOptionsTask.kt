package org.jetbrains.intellij.tasks

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.JavaExec

@CacheableTask
open class BuildSearchableOptionsTask : RunIdeBase(false) {

    private val traverseUIArgs = listOf("traverseUI")

    init {
        setArgs(traverseUIArgs)
    }

    override fun setArgs(applicationArgs: List<String>?): JavaExec =
        super.setArgs(traverseUIArgs.union(applicationArgs?.toList() ?: emptyList()))

    override fun setArgs(applicationArgs: MutableIterable<*>?): JavaExec =
        super.setArgs(traverseUIArgs.union(applicationArgs?.toList() ?: emptyList()))
}
