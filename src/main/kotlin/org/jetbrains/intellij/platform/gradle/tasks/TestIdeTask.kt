// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withNormalizer
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.asPath
import org.jetbrains.intellij.platform.gradle.propertyProviders.IntelliJPlatformArgumentProvider
import org.jetbrains.intellij.platform.gradle.propertyProviders.LaunchSystemArgumentProvider
import org.jetbrains.intellij.platform.gradle.propertyProviders.PluginPathArgumentProvider
import org.jetbrains.intellij.platform.gradle.tasks.base.*
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
abstract class TestIdeTask : Test(), CoroutinesJavaAgentAware, CustomPlatformVersionAware, JetBrainsRuntimeAware, SandboxAware {

    init {
        group = PLUGIN_GROUP_NAME
        description = "Runs the IDE instance with the developed plugin installed."
    }

    @TaskAction
    override fun executeTests() {
        assertPlatformVersion()

        super.executeTests()
    }

    override fun getExecutable(): String = jetbrainsRuntimeExecutable.asPath.absolutePathString()

    companion object {
        // TODO: define `inputs.property` for tasks to consider system properties in terms of the configuration cache
        //       see: https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.tasks/-task-inputs/property.html
        fun register(project: Project) =
            project.registerTask<TestIdeTask>(Tasks.TEST_IDE) {
                val sandboxDirectoryProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_TESTING_SANDBOX).get().sandboxDirectory

                enableAssertions = true

                jvmArgumentProviders.addAll(
                    listOf(
                        IntelliJPlatformArgumentProvider(intelliJPlatform, coroutinesJavaAgentFile, this),
                        LaunchSystemArgumentProvider(intelliJPlatform, sandboxDirectory, emptyList()),
                        PluginPathArgumentProvider(sandboxDirectory),
                    )
                )

                outputs.dir(sandboxDirectoryProvider.dir(IntelliJPluginConstants.Sandbox.SYSTEM))
                    .withPropertyName("System directory")
                inputs.dir(sandboxDirectoryProvider.dir(IntelliJPluginConstants.Sandbox.CONFIG))
                    .withPropertyName("Config Directory")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                inputs.files(sandboxDirectoryProvider.dir(IntelliJPluginConstants.Sandbox.PLUGINS))
                    .withPropertyName("Plugins directory")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                    .withNormalizer(ClasspathNormalizer::class)

//            systemProperty("idea.use.core.classloader.for.plugin.path", "true")
//            systemProperty("idea.force.use.core.classloader", "true")
//            systemProperty("idea.use.core.classloader.for", pluginIds.joinToString(","))
                systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")

                dependsOn(sandboxDirectoryProvider)
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
