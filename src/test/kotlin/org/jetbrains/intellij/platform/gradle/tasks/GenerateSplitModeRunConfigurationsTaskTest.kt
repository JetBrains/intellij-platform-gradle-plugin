// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.internal.project.ProjectInternal
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

class GenerateSplitModeRunConfigurationsTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `generate shared split mode run configurations`() {
        build(Tasks.GENERATE_SPLIT_MODE_RUN_CONFIGURATIONS)

        assertFileContent(
            dir.resolve(".run/${Tasks.RUN_IDE_BACKEND}.run.xml"),
            //language=xml
            """
            <component name="ProjectRunConfigurationManager">
              <configuration default="false" name="Run IDE (Backend)" type="GradleRunConfiguration" factoryName="Gradle">
                <log_file alias="Backend IDE logs" path="${'$'}PROJECT_DIR$/.intellijPlatform/sandbox/*/*/log_runIdeBackend/idea.log" show_all="true" skipped="false" />
                <ExternalSystemSettings>
                  <option name="executionName" />
                  <option name="externalProjectPath" value="${'$'}PROJECT_DIR${'$'}" />
                  <option name="externalSystemIdString" value="GRADLE" />
                  <option name="scriptParameters" value="--purge-old-log-directories" />
                  <option name="taskDescriptions">
                    <list />
                  </option>
                  <option name="taskNames">
                    <list>
                      <option value=":runIdeBackend" />
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
            """.trimIndent(),
        )

        assertFileContent(
            dir.resolve(".run/${Tasks.RUN_IDE_FRONTEND}.run.xml"),
            //language=xml
            """
            <component name="ProjectRunConfigurationManager">
              <configuration default="false" name="Run IDE (Frontend)" type="GradleRunConfiguration" factoryName="Gradle">
                <log_file alias="Frontend IDE logs" path="${'$'}PROJECT_DIR$/.intellijPlatform/sandbox/*/*/log_runIdeFrontend/frontend/*/idea.log" show_all="true" skipped="false" />
                <ExternalSystemSettings>
                  <option name="executionName" />
                  <option name="externalProjectPath" value="${'$'}PROJECT_DIR${'$'}" />
                  <option name="externalSystemIdString" value="GRADLE" />
                  <option name="scriptParameters" value="--purge-old-log-directories" />
                  <option name="taskDescriptions">
                    <list />
                  </option>
                  <option name="taskNames">
                    <list>
                      <option value=":runIdeFrontend" />
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
            """.trimIndent(),
        )

        assertFileContent(
            dir.resolve(".run/runIdeSplitMode.run.xml"),
            //language=xml
            """
            <component name="ProjectRunConfigurationManager">
              <configuration default="false" name="Run IDE (Split Mode)" type="CompoundRunConfigurationType" factoryName="Compound Run Configuration">
                <toRun type="GradleRunConfiguration" name="Run IDE (Backend)" />
                <toRun type="GradleRunConfiguration" name="Run IDE (Frontend)" />
                <method v="2" />
              </configuration>
            </component>
            """.trimIndent(),
        )
    }

    @Test
    fun `generate shared split mode run configurations for nested project`() {
        settingsFile write //language=kotlin
                """
                include("nestedProject")
                """.trimIndent()

        dir.resolve("nestedProject/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    id("java")
                    id("org.jetbrains.intellij.platform")
                }
                
                version = "1.0.0"
                
                repositories {
                    mavenCentral()
                
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        val useInstaller = providers.gradleProperty("intellijPlatform.useInstaller").orElse("true").map { it.toBoolean() }
                        create("$intellijPlatformType", "$intellijPlatformVersion") { this.useInstaller.set(useInstaller) }
                    }
                }
                
                intellijPlatform {
                    buildSearchableOptions = false
                    instrumentCode = false
                }
                """.trimIndent()

        build(":nestedProject:${Tasks.GENERATE_SPLIT_MODE_RUN_CONFIGURATIONS}")

        assertFileContent(
            dir.resolve("nestedProject/.run/${Tasks.RUN_IDE_BACKEND}.run.xml"),
            //language=xml
            """
            <component name="ProjectRunConfigurationManager">
              <configuration default="false" name="Run IDE (Backend)" type="GradleRunConfiguration" factoryName="Gradle">
                <log_file alias="Backend IDE logs" path="${'$'}PROJECT_DIR$/.intellijPlatform/sandbox/*/*/log_runIdeBackend/idea.log" show_all="true" skipped="false" />
                <ExternalSystemSettings>
                  <option name="executionName" />
                  <option name="externalProjectPath" value="${'$'}PROJECT_DIR${'$'}" />
                  <option name="externalSystemIdString" value="GRADLE" />
                  <option name="scriptParameters" value="--purge-old-log-directories" />
                  <option name="taskDescriptions">
                    <list />
                  </option>
                  <option name="taskNames">
                    <list>
                      <option value=":nestedProject:runIdeBackend" />
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
            """.trimIndent(),
        )

        assertFileContent(
            dir.resolve("nestedProject/.run/${Tasks.RUN_IDE_FRONTEND}.run.xml"),
            //language=xml
            """
            <component name="ProjectRunConfigurationManager">
              <configuration default="false" name="Run IDE (Frontend)" type="GradleRunConfiguration" factoryName="Gradle">
                <log_file alias="Frontend IDE logs" path="${'$'}PROJECT_DIR$/.intellijPlatform/sandbox/*/*/log_runIdeFrontend/frontend/*/idea.log" show_all="true" skipped="false" />
                <ExternalSystemSettings>
                  <option name="executionName" />
                  <option name="externalProjectPath" value="${'$'}PROJECT_DIR${'$'}" />
                  <option name="externalSystemIdString" value="GRADLE" />
                  <option name="scriptParameters" value="--purge-old-log-directories" />
                  <option name="taskDescriptions">
                    <list />
                  </option>
                  <option name="taskNames">
                    <list>
                      <option value=":nestedProject:runIdeFrontend" />
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
            """.trimIndent(),
        )
    }

    @Test
    fun `split mode run configuration generator task is hidden from default tasks report`() {
        build(ProjectInternal.TASKS_TASK) {
            assertNotContains(Tasks.GENERATE_SPLIT_MODE_RUN_CONFIGURATIONS, output)
        }
    }
}
