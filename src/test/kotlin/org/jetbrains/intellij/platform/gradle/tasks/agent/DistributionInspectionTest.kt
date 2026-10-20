// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformTestBase
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.*

class DistributionInspectionTest : IntelliJPlatformTestBase() {

    private fun plan(artifacts: List<DistributionArtifact> = emptyList()) = DistributionPlan(
        archivePath = null,
        rootDirectory = "plugin",
        artifacts = artifacts,
        dependencies = emptyList(),
        configurations = emptyMap(),
        nativeVariantsEnabled = false,
        nativeVariants = emptyList(),
    )

    private fun archive(entries: Map<String, String>) = dir.resolve("distribution.zip").apply {
        writeBytes(ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        })
    }.toFile()

    @Test
    fun `inline content modules retain dependencies without inventing descriptor paths`() {
        val descriptor = parseDistributionDescriptor("""
            <idea-plugin>
              <content>
                <module name="inline.module" loading="optional">
                  <dependencies><plugin id="inline.plugin"/><module name="core.module"/></dependencies>
                </module>
              </content>
              <module value="legacy.module.alias"/>
            </idea-plugin>
        """.trimIndent().toByteArray(), "META-INF/plugin.xml", "main", "declared")
        val inline = descriptor.modules.single { it.name == "inline.module" }
        assertTrue(inline.inline)
        assertNull(inline.descriptor)
        assertEquals(setOf("inline.plugin", "core.module"), inline.dependencies.map { it.id }.toSet())
        assertEquals(emptyList(), descriptor.references())
        assertNull(descriptor.metadata["id"])
        assertNull(descriptor.metadata["sinceBuild"])
    }

    @Test
    fun `missing optional and module descriptors remain explicit in inspected archive`() {
        val file = archive(mapOf("plugin/META-INF/plugin.xml" to """
            <idea-plugin>
              <depends optional="true" config-file="missing.xml">optional.plugin</depends>
              <content><module name="missing.module"/></content>
            </idea-plugin>
        """.trimIndent()))
        val inspected = inspectDistributionArchive(file, plan())
        assertEquals(setOf("main", "module", "optional"), inspected.descriptors.map { it.kind }.toSet())
        assertEquals(2, inspected.descriptors.count { it.state == "missing" })
        assertTrue(inspected.descriptors.all { it.provenance == "verified" })
    }

    @Test
    fun `archive comparison reports only observable candidate paths and keeps extras`() {
        val file = archive(mapOf("plugin/extra.txt" to "extra", "plugin/lib/main.jar.txt" to "present"))
        val inspected = inspectDistributionArchive(file, plan(listOf(
            DistributionArtifact("composedJar", "/planned/main.jar.txt", "plugin/lib/main.jar.txt"),
            DistributionArtifact("composedJar", "/planned/missing.jar", "plugin/lib/missing.jar"),
            DistributionArtifact("searchableOptionsCandidate", "/planned/optional.jar", "plugin/lib/optional.jar"),
        )))
        assertEquals(listOf("plugin/lib/main.jar.txt"), inspected.comparison.matched)
        assertEquals(listOf("plugin/lib/missing.jar"), inspected.comparison.missing)
        assertEquals(listOf("plugin/extra.txt"), inspected.comparison.unplanned)
    }

    @Test
    fun `archive traversal paths and remote descriptor references are rejected without extraction`() {
        assertFailsWith<IllegalArgumentException> { inspectDistributionArchive(archive(mapOf("../outside" to "unsafe")), plan()) }
        val file = archive(mapOf("plugin/META-INF/plugin.xml" to """
            <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude"><xi:include href="https://example.invalid/plugin.xml"/></idea-plugin>
        """.trimIndent()))
        assertFailsWith<IllegalArgumentException> { inspectDistributionArchive(file, plan()) }
    }

    @Test
    fun `external entities are rejected instead of reading local files or downloading`() {
        assertFails {
            parseDistributionDescriptor("""
                <!DOCTYPE idea-plugin [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <idea-plugin><description>&secret;</description></idea-plugin>
            """.trimIndent().toByteArray(), "META-INF/plugin.xml", "main", "declared")
        }
    }

    @Test
    fun `local descriptor reads are bounded and malformed XML is unavailable`() {
        val path = dir.resolve("plugin.xml")
        path.writeBytes("<idea-plugin>".toByteArray())
        assertEquals("invalid", readDistributionDescriptorFile(path.toFile(), "main", "declared").state)
        assertFailsWith<IllegalArgumentException> { readDistributionBytes(ByteArray(DISTRIBUTION_XML_LIMIT + 1).inputStream()) }
        assertEquals("missing", readDistributionDescriptorFile(dir.resolve("missing.xml").toFile(), "main", "declared").state)
    }

    @Test
    fun `native overlays preserve directory-relative destinations and expose missing roots`() {
        val root = dir.resolve("native").createDirectories()
        root.resolve("bin").createDirectories().resolve("tool").writeBytes(byteArrayOf(1))
        val variant = DistributionVariant("linux", "arm64", null, null, null, "1-linux-arm64", emptyList(), listOf(root.toString(), dir.resolve("absent").toString()), false)
        val observed = plan().copy(nativeVariants = listOf(variant), nativeVariantsEnabled = true).observeOverlays().nativeVariants.single()
        assertEquals("plugin/bin/tool", observed.overlays.single { it.kind == "nativeOverlay" }.destination)
        assertNull(observed.overlays.single { it.kind == "unavailableNativeOverlay" }.destination)
    }
}