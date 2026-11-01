// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class TestIdePluginDependenciesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `read required split-JAR module dependencies from an installed plugin directory`() {
        val plugin = createSplitPlugin()

        assertEquals(requiredIds, TestIdeTask.getRequiredPluginIds(listOf(plugin)))
    }

    @Test
    fun `read required split-JAR module dependencies from collected plugin JARs`() {
        val plugin = createSplitPlugin()

        assertEquals(
            requiredIds,
            TestIdeTask.getRequiredPluginIds(
                listOf(
                    plugin.resolve("lib/plugin.jar"),
                    plugin.resolve("lib/library.jar"),
                    plugin.resolve("lib/modules/required.module.jar"),
                    plugin.resolve("lib/modules/optional.module.jar"),
                ),
            ),
        )
        assertEquals(
            requiredIds,
            TestIdeTask.getRequiredPluginIds(listOf(plugin.resolve("lib/modules/required.module.jar"))),
        )
    }

    @Test
    fun `keep standalone plugin JAR dependencies and skip libraries and missing paths`() {
        val directory = temporaryFolder.newFolder("standalone").toPath()
        val plugin = directory.resolve("plugin.jar")
        plugin.writeJar("META-INF/plugin.xml" to pluginXml("<depends>standalone.dependency</depends>"))
        val library = directory.resolve("library.jar")
        library.writeJar("resource.txt" to "library")

        assertEquals(
            setOf("standalone.dependency"),
            TestIdeTask.getRequiredPluginIds(listOf(plugin, plugin, library, directory.resolve("missing.jar"))),
        )
    }

    private val requiredIds = setOf("main.dependency", "required.dependency", "bundled.module")

    private fun createSplitPlugin(): Path {
        val directory = temporaryFolder.newFolder("split-plugin").toPath()
        directory.resolve("lib/plugin.jar").writeJar(
            "META-INF/plugin.xml" to pluginXml(
                """
                <depends>main.dependency</depends>
                <content>
                    <module name="required.module" loading="required" />
                    <module name="optional.module" loading="optional" />
                </content>
                """.trimIndent(),
            ),
        )
        directory.resolve("lib/library.jar").writeJar("resource.txt" to "library")
        directory.resolve("lib/modules/required.module.jar").writeJar(
            "required.module.xml" to """
                <idea-plugin>
                    <dependencies>
                        <plugin id="required.dependency" />
                        <module name="bundled.module" />
                    </dependencies>
                </idea-plugin>
            """.trimIndent(),
        )
        directory.resolve("lib/modules/optional.module.jar").writeJar(
            "optional.module.xml" to """
                <idea-plugin>
                    <dependencies><plugin id="optional.dependency" /></dependencies>
                </idea-plugin>
            """.trimIndent(),
        )
        return directory
    }

    private fun pluginXml(content: String) = """
        <idea-plugin>
            <id>test</id>
            <name>Test</name>
            <version>1</version>
            <vendor>JetBrains</vendor>
            $content
        </idea-plugin>
    """.trimIndent()

    private fun Path.writeJar(vararg entries: Pair<String, String>) {
        parent.createDirectories()
        ZipOutputStream(outputStream()).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray())
                zip.closeEntry()
            }
        }
    }
}