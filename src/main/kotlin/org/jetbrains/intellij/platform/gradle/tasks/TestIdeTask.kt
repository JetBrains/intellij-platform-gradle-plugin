// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.argumentProviders.IntelliJPlatformArgumentProvider
import org.jetbrains.intellij.platform.gradle.argumentProviders.SandboxArgumentProvider
import org.jetbrains.intellij.platform.gradle.tasks.aware.CustomIntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware

/**
 * Runs plugin tests against the currently selected IntelliJ Platform with the built plugin loaded.
 * It directly extends the [Test] Gradle task, which allows for an extensive configuration (system properties, memory management, etc.).
 *
 * This task class also inherits from [CustomIntelliJPlatformVersionAware],
 * which makes it possible to create `testIde`-like tasks using custom IntelliJ Platform versions:
 *
 * ```kotlin
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
abstract class TestIdeTask : Test(), TestableAware, CustomIntelliJPlatformVersionAware {

    init {
        group = Plugin.GROUP_NAME
        description = "Runs the IDE instance with the developed plugin installed."
    }

    @TaskAction
    override fun executeTests() {
        validateIntelliJPlatformVersion()

        super.executeTests()
    }

    companion object : Registrable {
        private val Test.sourceTask: TestableAware
            get() = when {
                this is TestIdeTask -> this
                else -> project.tasks.named<PrepareTestTask>(Tasks.PREPARE_TEST).get()
            }

        private val Test.instrumentedCode
            get() = project.tasks.named<InstrumentCodeTask>(Constants.INSTRUMENT_CODE)
                .also { dependsOn(it) }
                .flatMap { it.outputDirectory }
                .let { project.files(it) }

        private val Test.instrumentedTestCode
            get() = project.tasks.named<InstrumentCodeTask>(Constants.INSTRUMENT_TEST_CODE)
                .also { dependsOn(it) }
                .flatMap { it.outputDirectory }
                .let { project.files(it) }

        private val Test.testCompileClasspathConfiguration
            get() = project.configurations[Configurations.External.TEST_COMPILE_CLASSPATH]

        val configuration: Test.() -> Unit = {
            enableAssertions = true

            jvmArgumentProviders.add(
                IntelliJPlatformArgumentProvider(
                    sourceTask.intelliJPlatformConfiguration,
                    sourceTask.coroutinesJavaAgentFile,
                    sourceTask.pluginXml,
                    sourceTask.runtimeArchitecture,
                    options = this,
                )
            )

            jvmArgumentProviders.add(
                SandboxArgumentProvider(
                    sourceTask.sandboxConfigDirectory,
                    sourceTask.sandboxPluginsDirectory,
                    sourceTask.sandboxSystemDirectory,
                    sourceTask.sandboxLogDirectory,
                )
            )

            systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
            systemProperty("idea.use.core.classloader.for.plugin.path", "true")
            systemProperty("idea.force.use.core.classloader", "true")
            // systemProperty("idea.use.core.classloader.for", pluginIds.joinToString(","))

            classpath = instrumentedCode + instrumentedTestCode + classpath + testCompileClasspathConfiguration
            testClassesDirs = instrumentedTestCode + testClassesDirs
            javaLauncher = sourceTask.runtimeLauncher
        }

        override fun register(project: Project) =
            project.registerTask<TestIdeTask>(configuration = configuration)
    }
}
