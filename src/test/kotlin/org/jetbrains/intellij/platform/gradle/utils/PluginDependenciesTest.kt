// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginDependenciesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `collect required dependencies with caller-specific content module policies`() {
        val path = temporaryFolder.newFile("plugin.jar").toPath()
        val modules = listOf("required", "optional", "on-demand", "conditional")
        val descriptor = """
            <idea-plugin>
                <id>test</id>
                <name>Test</name>
                <version>1</version>
                <vendor>JetBrains</vendor>
                <depends>legacy</depends>
                <depends optional="true">optional.legacy</depends>
                <dependencies>
                    <plugin id="modern" />
                    <module name="main.module" />
                </dependencies>
                <content>
                    <module name="required" loading="required" />
                    <module name="optional" loading="optional" />
                    <module name="on-demand" loading="on-demand" />
                    <module name="conditional" required-if-available="target.module" />
                </content>
            </idea-plugin>
        """.trimIndent()
        ZipOutputStream(path.outputStream()).use { zip ->
            (listOf("META-INF/plugin.xml" to descriptor) + modules.map { name ->
                "$name.xml" to """
                    <idea-plugin>
                        <dependencies><plugin id="$name.dependency" /></dependencies>
                    </idea-plugin>
                """.trimIndent()
            }).forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray())
                zip.closeEntry()
            }
        }

        withIdePluginManager { manager ->
            val plugin = requireNotNull(manager.safelyCreatePlugin(path).getOrNull())
            assertEquals(modules.toSet(), plugin.modulesDescriptors.map { it.moduleDefinition.name }.toSet())
            assertEquals(
                setOf("legacy", "modern", "main.module", "required.dependency"),
                plugin.requiredDependencyIds { it.moduleDefinition.loadingRule.required }.toSet(),
            )
            assertEquals(
                setOf("legacy", "modern", "main.module", "required.dependency", "conditional.dependency"),
                plugin.requiredDependencyIds {
                    it.moduleDefinition.loadingRule.required || it.moduleDefinition.requiredIfAvailable != null
                }.toSet(),
            )
        }
    }
}