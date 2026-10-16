// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.utils.JDOMUtil
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreparePluginVariantTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `append native dependencies to existing dependencies block without duplicates`() {
        val descriptor = prepareVariant(
            """
            <dependencies>
              <plugin id="com.intellij" />
              <plugin id="com.intellij.modules.os.linux" />
              <module name="intellij.platform.core" />
            </dependencies>
            """.trimIndent(),
        )

        assertTrue(descriptor.getChildren("depends").isEmpty())
        val dependencies = assertNotNull(descriptor.getChild("dependencies"))
        assertEquals(
            listOf("com.intellij", "com.intellij.modules.os.linux", "com.intellij.modules.arch.arm64"),
            dependencies.getChildren("plugin").map { it.getAttributeValue("id") },
        )
        assertEquals("intellij.platform.core", dependencies.getChild("module").getAttributeValue("name"))
        assertEquals(1, descriptor.getChildren("dependencies").size)
    }

    @Test
    fun `append native dependencies to empty dependencies block`() {
        val descriptor = prepareVariant("<dependencies />")

        assertTrue(descriptor.getChildren("depends").isEmpty())
        val dependencies = assertNotNull(descriptor.getChild("dependencies"))
        assertEquals(
            listOf("com.intellij.modules.os.linux", "com.intellij.modules.arch.arm64"),
            dependencies.getChildren("plugin").map { it.getAttributeValue("id") },
        )
        assertEquals(1, descriptor.getChildren("dependencies").size)
    }

    @Test
    fun `prefer dependencies block over legacy depends elements`() {
        val descriptor = prepareVariant(
            """
            <depends>com.intellij.modules.platform</depends>
            <dependencies>
              <plugin id="com.intellij" />
            </dependencies>
            """.trimIndent(),
        )

        assertEquals(listOf("com.intellij.modules.platform"), descriptor.getChildren("depends").map { it.textTrim })
        val dependencies = assertNotNull(descriptor.getChild("dependencies"))
        assertEquals(
            listOf("com.intellij", "com.intellij.modules.os.linux", "com.intellij.modules.arch.arm64"),
            dependencies.getChildren("plugin").map { it.getAttributeValue("id") },
        )
    }

    @Test
    fun `retain legacy depends format without duplicates`() {
        val descriptor = prepareVariant(
            """
            <depends>com.intellij.modules.platform</depends>
            <depends> com.intellij.modules.os.linux </depends>
            """.trimIndent(),
        )

        assertNull(descriptor.getChild("dependencies"))
        assertEquals(
            listOf("com.intellij.modules.platform", "com.intellij.modules.os.linux", "com.intellij.modules.arch.arm64"),
            descriptor.getChildren("depends").map { it.textTrim },
        )
    }

    @Test
    fun `use dependencies block when no dependency format is present`() {
        val descriptor = prepareVariant("")

        assertTrue(descriptor.getChildren("depends").isEmpty())
        val dependencies = assertNotNull(descriptor.getChild("dependencies"))
        assertEquals(
            listOf("com.intellij.modules.os.linux", "com.intellij.modules.arch.arm64"),
            dependencies.getChildren("plugin").map { it.getAttributeValue("id") },
        )
    }

    private fun prepareVariant(dependencies: String) = run {
        pluginXml write //language=xml
                """
                <idea-plugin>
                  <name>MyPluginName</name>
                  <vendor>JetBrains</vendor>
                  $dependencies
                </idea-plugin>
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration.ideaVersion.sinceBuild = "$nativeVariantsSinceBuild"
                    nativeVariants.enabled = true
                }
                """.trimIndent()

        build("${Tasks.PREPARE_PLUGIN_VARIANT}_linux_arm64")

        buildDirectory.resolve("intermediates/pluginVariants/linux-arm64/projectName-1.0.0.jar").toZip().use { jar ->
            JDOMUtil.loadDocument(fileText(jar, "META-INF/plugin.xml").byteInputStream()).rootElement.also {
                assertEquals("1.0.0-linux-arm64", it.getChildTextTrim("version"))
            }
        }
    }
}