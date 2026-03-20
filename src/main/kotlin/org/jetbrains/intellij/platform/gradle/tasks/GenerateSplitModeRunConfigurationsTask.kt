// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.asPath
import javax.inject.Inject
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Generates shared IntelliJ IDEA run configurations for split-mode frontend/backend runs and their compound wrapper.
 *
 * The task is intentionally left ungrouped so it can be invoked directly without polluting the standard Gradle tasks listing.
 */
@DisableCachingByDefault(because = "Small utility task that writes shared IntelliJ IDEA run configuration files.")
abstract class GenerateSplitModeRunConfigurationsTask @Inject constructor(
    objects: ObjectFactory,
    private val projectLayout: ProjectLayout,
) : DefaultTask() {

    private val projectPathProperty = objects.property(String::class.java)

    @get:Input
    val projectPath: String
        get() = projectPathProperty.get()

    @get:OutputFile
    val backendConfigurationFile: RegularFile
        get() = runConfigurationFile(Tasks.RUN_IDE_BACKEND)

    @get:OutputFile
    val frontendConfigurationFile: RegularFile
        get() = runConfigurationFile(Tasks.RUN_IDE_FRONTEND)

    @get:OutputFile
    val compoundConfigurationFile: RegularFile
        get() = runConfigurationFile(RUN_IDE_SPLIT_MODE_CONFIGURATION_FILE_NAME)

    @TaskAction
    fun generate() {
        runConfigurationsDirectory.asPath.createDirectories()

        backendConfigurationFile.asPath.writeText(
            gradleRunConfigurationXml(
                configurationName = BACKEND_CONFIGURATION_NAME,
                taskPath = qualifiedTaskPath(Tasks.RUN_IDE_BACKEND),
            )
        )
        frontendConfigurationFile.asPath.writeText(
            gradleRunConfigurationXml(
                configurationName = FRONTEND_CONFIGURATION_NAME,
                taskPath = qualifiedTaskPath(Tasks.RUN_IDE_FRONTEND),
            )
        )
        compoundConfigurationFile.asPath.writeText(
            compoundRunConfigurationXml()
        )
    }

    private val runConfigurationsDirectory: Directory
        get() = projectLayout.projectDirectory.dir(RUN_CONFIGURATIONS_DIRECTORY)

    private fun gradleRunConfigurationXml(
        configurationName: String,
        taskPath: String,
    ) = """
        <component name="ProjectRunConfigurationManager">
          <configuration default="false" name="$configurationName" type="GradleRunConfiguration" factoryName="Gradle">
            <ExternalSystemSettings>
              <option name="executionName" />
              <option name="externalProjectPath" value="$PROJECT_DIR_MACRO" />
              <option name="externalSystemIdString" value="GRADLE" />
              <option name="scriptParameters" value="" />
              <option name="taskDescriptions">
                <list />
              </option>
              <option name="taskNames">
                <list>
                  <option value="$taskPath" />
                </list>
              </option>
              <option name="vmOptions" value="" />
            </ExternalSystemSettings>
            <ExternalSystemDebugServerProcess>true</ExternalSystemDebugServerProcess>
            <ExternalSystemReattachDebugProcess>true</ExternalSystemReattachDebugProcess>
            <DebugAllEnabled>false</DebugAllEnabled>
            <RunAsTest>false</RunAsTest>
            <method v="2" />
          </configuration>
        </component>
    """.trimIndent() + "\n"

    private fun compoundRunConfigurationXml() = """
        <component name="ProjectRunConfigurationManager">
          <configuration default="false" name="$RUN_IDE_SPLIT_MODE_CONFIGURATION_NAME" type="CompoundRunConfigurationType" factoryName="Compound Run Configuration">
            <toRun type="GradleRunConfiguration" name="$BACKEND_CONFIGURATION_NAME" />
            <toRun type="GradleRunConfiguration" name="$FRONTEND_CONFIGURATION_NAME" />
            <method v="2" />
          </configuration>
        </component>
    """.trimIndent() + "\n"

    private fun runConfigurationFile(name: String) =
        runConfigurationsDirectory.file("$name$RUN_CONFIGURATION_FILE_SUFFIX")

    private fun qualifiedTaskPath(taskName: String) = when (projectPath) {
        ":" -> ":$taskName"
        else -> "$projectPath:$taskName"
    }

    companion object : Registrable {
        private const val BACKEND_CONFIGURATION_NAME = "Run IDE (Backend)"
        private const val FRONTEND_CONFIGURATION_NAME = "Run IDE (Frontend)"
        private const val RUN_IDE_SPLIT_MODE_CONFIGURATION_NAME = "Run IDE (Split Mode)"
        private const val RUN_IDE_SPLIT_MODE_CONFIGURATION_FILE_NAME = "runIdeSplitMode"
        private const val RUN_CONFIGURATIONS_DIRECTORY = ".run"
        private const val RUN_CONFIGURATION_FILE_SUFFIX = ".run.xml"
        private const val PROJECT_DIR_MACRO = $$"$PROJECT_DIR$"

        override fun register(project: Project) =
            project.registerTask<GenerateSplitModeRunConfigurationsTask>(
                Tasks.GENERATE_SPLIT_MODE_RUN_CONFIGURATIONS,
                configureWithType = false,
            ) {
                projectPathProperty.convention(project.path)
            }
    }
}
