// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.aware.CustomIntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.RunnableIdeAware
import org.jetbrains.intellij.platform.gradle.utils.asPath
import kotlin.io.path.absolutePathString

/**
 * Runs plugin tests against the currently selected IntelliJ Platform with the built plugin loaded.
 * It directly extends the [Test] Gradle task, which allows for an extensive configuration (system properties, memory management, etc.).
 *
 * This task class also inherits from [CustomIntelliJPlatformVersionAware],
 * which makes it possible to create `testIde`-like tasks using custom IntelliJ Platform versions:
 *
 * ```
 * import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
 * import org.jetbrains.intellij.platform.gradle.tasks.TestIdeTask
 *
 * tasks {
 *   val testPhpStorm by registering(TestIdeTask::class) {
 *     type = IntelliJPlatformType.PhpStorm
 *     version = "2023.2.2"
 *   }
 *
 *   val testLocalIde by registering(TestIdeTask::class) {
 *     localPath = file("/Users/hsz/Applications/Android Studio.app")
 *   }
 * }
 * ```
 */
@UntrackedTask(because = "Should always run")
abstract class TestIdeTask : Test(), RunnableIdeAware, CustomIntelliJPlatformVersionAware {

    init {
        group = PLUGIN_GROUP_NAME
        description = "Runs the IDE instance with the developed plugin installed."
    }

    @TaskAction
    override fun executeTests() {
        validateIntelliJPlatformVersion()

        super.executeTests()
    }

    override fun getExecutable() = runtimeExecutable.asPath.absolutePathString()

    companion object : Registrable {
        // TODO: define `inputs.property` for tasks to consider system properties in terms of the configuration cache
        //       see: https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.tasks/-task-inputs/property.html
        override fun register(project: Project) =
            project.registerTask<TestIdeTask>(Tasks.TEST_IDE) {

//            systemProperty("idea.use.core.classloader.for.plugin.path", "true")
//            systemProperty("idea.force.use.core.classloader", "true")
//            systemProperty("idea.use.core.classloader.for", pluginIds.joinToString(","))

                project.tasks.named<Test>("test").configure {
                    finalizedBy(this@registerTask)
                }
//            finalizedBy(IntelliJPluginConstants.CLASSPATH_INDEX_CLEANUP_TASK_NAME)

//            classpath = instrumentedCodeOutputsProvider.get() + instrumentedTestCodeOutputsProvider.get() + classpath
//            testClassesDirs = instrumentedTestCodeOutputsProvider.get() + testClassesDirs

//            doFirst {
//                classpath += ideaDependencyLibrariesProvider.get() +
//                        ideaConfigurationFiles.get() +
//                        ideaPluginsConfigurationFiles.get() +
//                        ideaClasspathFiles.get()
//            }
            }
    }
}
