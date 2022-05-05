// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory

@CacheableTask
open class BuildSearchableOptionsTask : RunIdeBase(false) {

    @OutputDirectory
    val outputDir: DirectoryProperty = objectFactory.directoryProperty()

    private val traverseUIArgs = listOf("traverseUI")

    init {
        args = traverseUIArgs
    }

    override fun exec() {
        args = args + listOf(outputDir.get().asFile.canonicalPath, "true")
        super.exec()
    }

    override fun setArgs(applicationArgs: List<String>?): JavaExec =
        super.setArgs(traverseUIArgs.union(applicationArgs?.toList() ?: emptyList()))

    override fun setArgs(applicationArgs: MutableIterable<*>?): JavaExec =
        super.setArgs(traverseUIArgs.union(applicationArgs?.toList() ?: emptyList()))
}
