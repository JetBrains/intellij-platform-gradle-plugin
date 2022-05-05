// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.plugins.JavaPlugin
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@Suppress("GroovyUnusedAssignment", "PluginXmlValidity")
class ProcessResourcesTaskSpec : IntelliJPluginSpecBase() {

    private val outputPluginXml = lazy { File(buildDirectory, "resources/main/META-INF/").listFiles()?.first() }

    @Test
    fun `use patched plugin xml files`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        assertFileContent(outputPluginXml.value, """
            <idea-plugin>
              <idea-version since-build="212.5712" until-build="212.*" />
            </idea-plugin>
        """)
    }

    @Test
    fun `do not break incremental processing`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        val result = build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        assertEquals(
            TaskOutcome.UP_TO_DATE,
            result.task(":${JavaPlugin.PROCESS_RESOURCES_TASK_NAME}")?.outcome,
        )
    }

    @Test
    fun `update resources on updated patched xml files`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            version = '0.42.123'
        """)

        build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        buildFile.groovy("""
            patchPluginXml { sinceBuild = 'Oh' }
        """)

        val result = build(JavaPlugin.PROCESS_RESOURCES_TASK_NAME)

        assertNotEquals(
            TaskOutcome.UP_TO_DATE,
            result.task(":${JavaPlugin.PROCESS_RESOURCES_TASK_NAME}")?.outcome,
        )

        assertFileContent(outputPluginXml.value, """
            <idea-plugin>
              <version>0.42.123</version>
              <idea-version since-build="Oh" until-build="212.*" />
            </idea-plugin>
        """)
    }
}
