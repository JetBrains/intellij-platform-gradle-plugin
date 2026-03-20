// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.asPath
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Generates shared IntelliJ IDEA run configurations for split-mode frontend/backend runs and their compound wrapper.
 *
 * The task is intentionally left ungrouped so it can be invoked directly without polluting the standard Gradle tasks listing.
 */
@DisableCachingByDefault(because = "Small utility task that writes shared IntelliJ IDEA run configuration files.")
abstract class GenerateSplitModeRunConfigurationsTask : DefaultTask() {

    @get:Input
    abstract val backendConfigurationName: Property<String>

    @get:Input
    abstract val frontendConfigurationName: Property<String>

    @get:Input
    abstract val compoundConfigurationName: Property<String>

    @get:Input
    abstract val backendTaskPath: Property<String>

    @get:Input
    abstract val frontendTaskPath: Property<String>

    @get:Input
    abstract val externalProjectPath: Property<String>

    @get:OutputFile
    abstract val backendConfigurationFile: RegularFileProperty

    @get:OutputFile
    abstract val frontendConfigurationFile: RegularFileProperty

    @get:OutputFile
    abstract val compoundConfigurationFile: RegularFileProperty

    @get:Internal
    abstract val runConfigurationsDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        runConfigurationsDirectory.asFile.get().toPath().createDirectories()

        backendConfigurationFile.asPath.writeText(
            gradleRunConfigurationXml(
                configurationName = backendConfigurationName.get(),
                taskPath = backendTaskPath.get(),
            )
        )
        frontendConfigurationFile.asPath.writeText(
            gradleRunConfigurationXml(
                configurationName = frontendConfigurationName.get(),
                taskPath = frontendTaskPath.get(),
            )
        )
        compoundConfigurationFile.asPath.writeText(
            compoundRunConfigurationXml(
                configurationName = compoundConfigurationName.get(),
                backendConfigurationName = backendConfigurationName.get(),
                frontendConfigurationName = frontendConfigurationName.get(),
            )
        )
    }

    private fun gradleRunConfigurationXml(
        configurationName: String,
        taskPath: String,
    ) = """
        <component name="ProjectRunConfigurationManager">
          <configuration default="false" name="$configurationName" type="GradleRunConfiguration" factoryName="Gradle">
            <ExternalSystemSettings>
              <option name="executionName" />
              <option name="externalProjectPath" value="${externalProjectPath.get()}" />
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

    private fun compoundRunConfigurationXml(
        configurationName: String,
        backendConfigurationName: String,
        frontendConfigurationName: String,
    ) = """
        <component name="ProjectRunConfigurationManager">
          <configuration default="false" name="$configurationName" type="CompoundRunConfigurationType" factoryName="Compound Run Configuration">
            <toRun type="GradleRunConfiguration" name="$backendConfigurationName" />
            <toRun type="GradleRunConfiguration" name="$frontendConfigurationName" />
            <method v="2" />
          </configuration>
        </component>
    """.trimIndent() + "\n"

    companion object : Registrable {
        private const val BACKEND_CONFIGURATION_NAME = "Run IDE (Backend)"
        private const val FRONTEND_CONFIGURATION_NAME = "Run IDE (Frontend)"
        private const val RUN_IDE_SPLIT_MODE_CONFIGURATION_NAME = "Run IDE (Split Mode)"
        private const val RUN_IDE_SPLIT_MODE_CONFIGURATION_FILE_NAME = "runIdeSplitMode"
        private const val RUN_CONFIGURATIONS_DIRECTORY = ".run"
        private const val RUN_CONFIGURATION_FILE_SUFFIX = ".run.xml"
        private const val PROJECT_DIR_MACRO = "\$PROJECT_DIR$"

        override fun register(project: Project) =
            project.registerTask<GenerateSplitModeRunConfigurationsTask>(
                Tasks.GENERATE_SPLIT_MODE_RUN_CONFIGURATIONS,
                configureWithType = false,
            ) {
                val outputDirectory = project.layout.projectDirectory.dir(RUN_CONFIGURATIONS_DIRECTORY)

                backendConfigurationName.convention(BACKEND_CONFIGURATION_NAME)
                frontendConfigurationName.convention(FRONTEND_CONFIGURATION_NAME)
                compoundConfigurationName.convention(RUN_IDE_SPLIT_MODE_CONFIGURATION_NAME)
                backendTaskPath.convention(project.provider { project.qualifiedTaskPath(Tasks.RUN_IDE_BACKEND) })
                frontendTaskPath.convention(project.provider { project.qualifiedTaskPath(Tasks.RUN_IDE_FRONTEND) })
                externalProjectPath.convention(PROJECT_DIR_MACRO)

                runConfigurationsDirectory.convention(outputDirectory)
                backendConfigurationFile.convention(outputDirectory.file("${Tasks.RUN_IDE_BACKEND}$RUN_CONFIGURATION_FILE_SUFFIX"))
                frontendConfigurationFile.convention(outputDirectory.file("${Tasks.RUN_IDE_FRONTEND}$RUN_CONFIGURATION_FILE_SUFFIX"))
                compoundConfigurationFile.convention(outputDirectory.file("$RUN_IDE_SPLIT_MODE_CONFIGURATION_FILE_NAME$RUN_CONFIGURATION_FILE_SUFFIX"))
            }

        private fun Project.qualifiedTaskPath(taskName: String) = when (path) {
            ":" -> ":$taskName"
            else -> "$path:$taskName"
        }
    }
}
