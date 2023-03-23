// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.deleteQuietly
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.info
import org.jetbrains.intellij.logCategory

/**
 * Remove `classpath.index` files that are created by the `PathClassLoader`.
 * This loader, due to the implementation bug, ignores the `idea.classpath.index.enabled=false` flag and as a workaround, files have to be removed manually.
 */
@DisableCachingByDefault(because = "Deletion cannot be cached")
abstract class ClasspathIndexCleanupTask : DefaultTask() {

    /**
     * The list of `classpath.index` files to be removed.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classpathIndexFiles: ConfigurableFileCollection

    private val context = logCategory()

    init {
        group = PLUGIN_GROUP_NAME
        description = "Removes classpath.index files created by PathClassLoader"
    }

    @TaskAction
    fun classpathIndexCleanup() {
        classpathIndexFiles.forEach {
            it.toPath().deleteQuietly()
            info(context, "Removed classpath.index file: $it")
        }
    }
}
