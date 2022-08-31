// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.deleteQuietly
import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.info
import org.jetbrains.intellij.logCategory
import javax.inject.Inject

/**
 * Remove `classpath.index` files that are created by the `PathClassLoader`.
 * This loader, due to the implementation bug, ignores the `idea.classpath.index.enabled=false` flag and as a workaround,
 * files have to be removed manually.
 */
open class ClasspathIndexCleanupTask @Inject constructor(
    objectFactory: ObjectFactory,
) : ConventionTask() {

    @get:InputFiles
    val classpathIndexFiles = objectFactory.fileCollection()

    private val context = logCategory()

    @TaskAction
    fun classpathIndexCleanup() {
        classpathIndexFiles.forEach {
            it.toPath().deleteQuietly()
            info(context, "Removed classpath.index file: $it")
        }
    }
}
