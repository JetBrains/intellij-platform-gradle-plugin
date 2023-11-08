// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.base.utils.listFiles
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@Suppress("GroovyUnusedAssignment", "PluginXmlValidity", "ComplexRedundantLet")
class ProcessResourcesTaskSpec : IntelliJPluginSpecBase() {

    private val outputPluginXml = lazy { buildDirectory.resolve("resources/main/META-INF/").listFiles().first() }

    @Test
    fun `use patched plugin xml files`() {
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        assertFileContent(
            outputPluginXml.value,
            """
            <idea-plugin>
              <idea-version since-build="221.6008" until-build="221.*" />
            </idea-plugin>
            """.trimIndent()
        )
    }

    @Test
    fun `do not break incremental processing`() {
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME).let {
            assertEquals(
                TaskOutcome.UP_TO_DATE,
                it.task(":${JavaPlugin.PROCESS_RESOURCES_TASK_NAME}")?.outcome,
            )
        }
    }

    @Test
    fun `update resources on updated patched xml files`() {
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            """.trimIndent()
        )

        build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        buildFile.groovy(
            """
            patchPluginXml { sinceBuild = 'Oh' }
            """.trimIndent()
        )

        build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME).let {
            assertNotEquals(
                TaskOutcome.UP_TO_DATE,
                it.task(":${JavaPlugin.PROCESS_RESOURCES_TASK_NAME}")?.outcome,
            )

            assertFileContent(
                outputPluginXml.value,
                """
                <idea-plugin>
                  <version>0.42.123</version>
                  <idea-version since-build="Oh" until-build="221.*" />
                </idea-plugin>
                """.trimIndent()
            )
        }
    }
}
