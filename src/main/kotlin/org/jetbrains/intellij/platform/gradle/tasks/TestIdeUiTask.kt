// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware
import org.jetbrains.intellij.platform.gradle.utils.dependenciesHelper

/**
 * Runs the IDE instance with the developed plugin and Starter framework for UI testing.
 *
 * Creates or reuses an isolated source set matching the task name (`src/<taskName>`) and uses JUnit Platform by default.
 * The default `testUi` task uses `src/testUi`; a custom `extraUi` task uses `src/extraUi` and its own dependencies.
 * Starter and Driver dependencies are supplied automatically with the standard IDE-provided exclusions;
 * the source set compiles against the IntelliJ Platform classpath, and the host JVM classpath appends the task's
 * IntelliJ Platform classpath after its source set's runtime classpath, so excluded libraries are provided
 * by the same IDE the test targets.
 *
 * Starter requires the JUnit Platform and declares JUnit Jupiter and the launcher as its own dependencies,
 * so no test engine is added or filtered by the plugin: any JUnit Platform engine present on the source set's
 * classpath is discovered. When Starter brings the JUnit Vintage engine, add JUnit 4 explicitly to
 * `<taskName>RuntimeOnly`, as the standard IDE-provided exclusions remove it from Starter's dependency graph.
 * Explicit task classpath and test-classes overrides remain supported.
 * This task is not attached to `check`, `test`, or `build`.
 *
 * The test JVM receives `path.to.build.plugin` pointing to [archiveFile] and, unless explicitly set,
 * `ide.test.version` containing the selected IntelliJ Platform's [productInfo] version.
 */
@Incubating
@UntrackedTask(because = "Should always run")
abstract class TestIdeUiTask : Test(), TestableAware {

    /**
     * Specifies the archive file representing the input file to be tested.
     *
     * Default value: [BuildPluginTask.archiveFile]
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archiveFile: RegularFileProperty

    /**
     * Executes the task, configures and runs the IDE.
     */
    @TaskAction
    override fun executeTests() {
        validateIntelliJPlatformVersion()

        systemProperty("path.to.build.plugin", archiveFile.get())
        if ("ide.test.version" !in systemProperties) {
            systemProperty("ide.test.version", productInfo.version)
        }

        super.executeTests()
    }

    init {
        project.the<JavaPluginExtension>().sourceSets.maybeCreate(name)
        group = Plugin.GROUP_NAME
        description = "Runs the IDE instance with the developed plugin and Starter framework for UI testing."
    }

    companion object : Registrable {
        override fun register(project: Project) {
            val buildPluginTaskProvider = project.tasks.named<BuildPluginTask>(Tasks.BUILD_PLUGIN)
            val sourceSets = project.the<JavaPluginExtension>().sourceSets.also {
                it.maybeCreate(Tasks.TEST_UI)
            }

            project.registerTask<TestIdeUiTask>(Tasks.TEST_UI) {
                val testIdeUi = sourceSets.getByName(name).also {
                    it.compileClasspath += intellijPlatformClasspathConfiguration
                }
                project.dependenciesHelper.addTestFrameworkDependency(
                    type = TestFrameworkType.Starter,
                    versionProvider = project.providers.provider { Constraints.CLOSEST_VERSION },
                    configurationName = testIdeUi.implementationConfigurationName,
                )

                archiveFile.convention(buildPluginTaskProvider.flatMap { it.archiveFile })
                testClassesDirs = testIdeUi.output.classesDirs
                // Starter runs in a separate host JVM. Libraries excluded from its dependency graph as IDE-provided
                // (Kotlin stdlib, coroutines, TeamCity service messages, platform modules) come from the selected IDE.
                classpath = project.files(project.provider { testIdeUi.runtimeClasspath }, intellijPlatformClasspathConfiguration)
                useJUnitPlatform()
            }
        }
    }
}
