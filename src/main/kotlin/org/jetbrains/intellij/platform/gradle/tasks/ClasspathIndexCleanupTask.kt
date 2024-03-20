// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.base.utils.deleteQuietly
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.utils.Logger

/**
 * Remove `classpath.index` files that are created by the `PathClassLoader`.
 *
 * This loader, due to the implementation bug, ignores the `idea.classpath.index.enabled=false` flag and as a workaround, files have to be removed manually.
 *
 * Task is enabled if [IntelliJPluginExtension.version] is set to `2022.1` or higher.
 */
@Deprecated(message = "CHECK")
@DisableCachingByDefault(because = "Deletion cannot be cached")
abstract class ClasspathIndexCleanupTask : DefaultTask() {

    /**
     * The list of `classpath.index` files to be removed.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classpathIndexFiles: ConfigurableFileCollection

    private val log = Logger(javaClass)

    init {
        group = Plugin.GROUP_NAME
        description = "Removes classpath.index files created by PathClassLoader"
    }

    @TaskAction
    fun classpathIndexCleanup() {
        classpathIndexFiles.forEach {
            it.toPath().deleteQuietly()
            log.info("Removed classpath.index file: $it")
        }
    }

//    {
//        info("Configuring classpath.index cleanup task")
//
//        project.tasks.register<ClasspathIndexCleanupTask>(IntelliJPluginConstants.CLASSPATH_INDEX_CLEANUP_TASK_NAME)
//        project.tasks.withType<ClasspathIndexCleanupTask> {
//            classpathIndexFiles.from(project.provider {
//                (project.extensions.findByName("sourceSets") as SourceSetContainer)
//                    .flatMap {
//                        it.output.classesDirs + it.output.generatedSourcesDirs + project.files(
//                            it.output.resourcesDir
//                        )
//                    }
//                    .mapNotNull { dir ->
//                        dir
//                            .resolve("classpath.index")
//                            .takeIf { it.exists() }
//                    }
//            })
//        }
//    }
}
