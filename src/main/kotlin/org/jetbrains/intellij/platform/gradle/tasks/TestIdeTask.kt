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
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware
import org.jetbrains.intellij.platform.gradle.utils.IntelliJPlatformJavaLauncher

/**
 * Runs plugin tests against the currently selected IntelliJ Platform with the built plugin loaded.
 * It directly extends the [Test] Gradle task, which allows for an extensive configuration (system properties, memory management, etc.).
 */
@UntrackedTask(because = "Should always run")
abstract class TestIdeTask : Test(), TestableAware, IntelliJPlatformVersionAware {

    @TaskAction
    override fun executeTests() {
        validateIntelliJPlatformVersion()

        super.executeTests()
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Runs tests using a custom IDE instance with the developed plugin installed."
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

        internal val configuration: Test.() -> Unit = {
            enableAssertions = true

            jvmArgumentProviders.add(
                IntelliJPlatformArgumentProvider(
                    sourceTask.intelliJPlatformConfiguration,
                    sourceTask.coroutinesJavaAgentFile,
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

            systemProperty("idea.classpath.index.enabled", "false")
            systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
            systemProperty("idea.use.core.classloader.for.plugin.path", "true")
            systemProperty("idea.force.use.core.classloader", "true")
            // systemProperty("idea.use.core.classloader.for", pluginIds.joinToString(","))

            classpath = instrumentedCode + instrumentedTestCode + classpath + testCompileClasspathConfiguration
            testClassesDirs = instrumentedTestCode + testClassesDirs
            javaLauncher = sourceTask.runtimeDirectory.zip(sourceTask.runtimeMetadata) { directory, metadata ->
                IntelliJPlatformJavaLauncher(directory, metadata)
            }
        }

        override fun register(project: Project) =
            project.registerTask<TestIdeTask>(configuration = configuration)
    }
}
