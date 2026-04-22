// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
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
    abstract val projectPath: Property<String>

    @get:OutputFile
    abstract val backendConfigurationFile: RegularFileProperty

    @get:OutputFile
    abstract val frontendConfigurationFile: RegularFileProperty

    @get:OutputFile
    abstract val compoundConfigurationFile: RegularFileProperty

    @TaskAction
    fun generate() {
        backendConfigurationFile.asPath.apply {
            parent.createDirectories()
            writeText(
                gradleRunConfigurationXml(
                    configurationName = BACKEND_CONFIGURATION_NAME,
                    taskPath = qualifiedTaskPath(Tasks.RUN_IDE_BACKEND),
                ),
            )
        }
        frontendConfigurationFile.asPath.apply {
            parent.createDirectories()
            writeText(
                gradleRunConfigurationXml(
                    configurationName = FRONTEND_CONFIGURATION_NAME,
                    taskPath = qualifiedTaskPath(Tasks.RUN_IDE_FRONTEND),
                ),
            )
        }
        compoundConfigurationFile.asPath.apply {
            parent.createDirectories()
            writeText(compoundRunConfigurationXml())
        }
    }

    private fun additionalLogsTab(configurationName: String): String {
        return when (configurationName) {
            FRONTEND_CONFIGURATION_NAME -> "<log_file alias=\"Frontend IDE logs\" path=\"\$PROJECT_DIR\$/.intellijPlatform/sandbox/*/*/log_runIdeFrontend/frontend/*/idea.log\" show_all=\"true\" skipped=\"false\" />"
            BACKEND_CONFIGURATION_NAME -> "<log_file alias=\"Backend IDE logs\" path=\"\$PROJECT_DIR\$/.intellijPlatform/sandbox/*/*/log_runIdeBackend/idea.log\" show_all=\"true\" skipped=\"false\" />"
            else -> ""
        }
    }

    private fun gradleRunConfigurationXml(configurationName: String, taskPath: String) = //language=XML
        """
        <component name="ProjectRunConfigurationManager">
          <configuration default="false" name="$configurationName" type="GradleRunConfiguration" factoryName="Gradle">
            ${additionalLogsTab(configurationName)}
            <ExternalSystemSettings>
              <option name="executionName" />
              <option name="externalProjectPath" value="$PROJECT_DIR_MACRO" />
              <option name="externalSystemIdString" value="GRADLE" />
              <option name="scriptParameters" value="--$PURGE_OLD_LOG_DIRECTORIES_OPTION" />
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

    private fun compoundRunConfigurationXml() = //language=XML
        """
        <component name="ProjectRunConfigurationManager">
          <configuration default="false" name="$RUN_IDE_SPLIT_MODE_CONFIGURATION_NAME" type="CompoundRunConfigurationType" factoryName="Compound Run Configuration">
            <toRun type="GradleRunConfiguration" name="$BACKEND_CONFIGURATION_NAME" />
            <toRun type="GradleRunConfiguration" name="$FRONTEND_CONFIGURATION_NAME" />
            <method v="2" />
          </configuration>
        </component>
        """.trimIndent() + "\n"

    private fun qualifiedTaskPath(taskName: String) = "${projectPath.get()}:$taskName".replace("::", ":")

    companion object : Registrable {
        private const val BACKEND_CONFIGURATION_NAME = "Run IDE (Backend)"
        private const val FRONTEND_CONFIGURATION_NAME = "Run IDE (Frontend)"
        private const val RUN_IDE_SPLIT_MODE_CONFIGURATION_NAME = "Run IDE (Split Mode)"
        private const val RUN_IDE_SPLIT_MODE_CONFIGURATION_FILE_NAME = "runIdeSplitMode"
        private const val RUN_CONFIGURATIONS_DIRECTORY = ".run"
        private const val RUN_CONFIGURATION_FILE_SUFFIX = ".run.xml"
        private const val PROJECT_DIR_MACRO = $$"$PROJECT_DIR$"

        private fun Project.runConfigurationFile(name: String) =
            layout.projectDirectory.file("$RUN_CONFIGURATIONS_DIRECTORY/$name$RUN_CONFIGURATION_FILE_SUFFIX")

        override fun register(project: Project) =
            project.registerTask<GenerateSplitModeRunConfigurationsTask>(
                Tasks.GENERATE_SPLIT_MODE_RUN_CONFIGURATIONS,
                configureWithType = false,
            ) {
                projectPath.convention(project.path)
                backendConfigurationFile.convention(project.runConfigurationFile(Tasks.RUN_IDE_BACKEND))
                frontendConfigurationFile.convention(project.runConfigurationFile(Tasks.RUN_IDE_FRONTEND))
                compoundConfigurationFile.convention(
                    project.runConfigurationFile(
                        RUN_IDE_SPLIT_MODE_CONFIGURATION_FILE_NAME,
                    ),
                )
            }
    }
}
