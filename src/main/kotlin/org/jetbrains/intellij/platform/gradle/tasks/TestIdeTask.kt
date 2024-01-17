// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.TEST_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.tasks.base.CustomPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.base.RunIdeBase
import org.jetbrains.intellij.platform.gradle.tasks.base.RunnableIdeAware
import kotlin.io.path.absolutePathString

/**
 * Runs the IDE instance with the developed plugin installed.
 *
 * `runIde` task extends the [JavaExec] Gradle task – all properties available in the [JavaExec] as well as the following ones can be used to configure the [TestIdeTask] task.
 *
 * @see [RunIdeBase]
 * @see [JavaExec]
 */
@Deprecated(message = "CHECK")
@UntrackedTask(because = "Should always run guest IDE")
abstract class TestIdeTask : Test(), RunnableIdeAware, CustomPlatformVersionAware {

    init {
        group = PLUGIN_GROUP_NAME
        description = "Runs the IDE instance with the developed plugin installed."
    }

    @TaskAction
    override fun executeTests() {
        assertPlatformVersion()

        super.executeTests()
    }

    override fun getExecutable() = runtimeExecutable.asPath.absolutePathString()

    companion object {
        // TODO: define `inputs.property` for tasks to consider system properties in terms of the configuration cache
        //       see: https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.tasks/-task-inputs/property.html
        fun register(project: Project) =
            project.registerTask<TestIdeTask>(Tasks.TEST_IDE) {

//            systemProperty("idea.use.core.classloader.for.plugin.path", "true")
//            systemProperty("idea.force.use.core.classloader", "true")
//            systemProperty("idea.use.core.classloader.for", pluginIds.joinToString(","))

                project.tasks.named<Test>(TEST_TASK_NAME).configure {
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
