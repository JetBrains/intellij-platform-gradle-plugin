// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.ModuleDescriptor
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class ModuleDescriptorCoordinatesTest {

    @Test
    fun `ignore legacy library descriptor names without module prefix`() {
        val descriptor = ModuleDescriptor(
            name = "jaxb-api",
            namespace = "\$legacy_jps_library",
            visibility = "public",
            dependencies = emptyList(),
            resources = resources("../lib/jaxb-api.jar"),
        )

        assertNull(descriptor.toCoordinatesOrNull())
    }

    @Test
    fun `convert jps module descriptor into coordinates`() {
        val descriptor = ModuleDescriptor(
            name = "intellij.platform.codeStyle",
            namespace = "jps",
            visibility = "public",
            dependencies = emptyList(),
            resources = resources("../lib/codeStyle.jar"),
        )

        assertEquals(
            Coordinates("com.jetbrains.intellij.platform", "code-style"),
            descriptor.toCoordinatesOrNull(),
        )
    }

    @Test
    fun `convert descriptor without namespace into coordinates`() {
        val descriptor = ModuleDescriptor(
            name = "intellij.platform.boot",
            namespace = null,
            visibility = "public",
            dependencies = emptyList(),
            resources = resources("../lib/platform-loader.jar"),
        )

        assertEquals(
            Coordinates("com.jetbrains.intellij.platform", "boot"),
            descriptor.toCoordinatesOrNull(),
        )
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `load module descriptor coordinates only from supported namespaces and collected jars`() {
        val platformPath = createTempDirectory("platform")

        try {
            val moduleDescriptorsPath = platformPath.resolve("modules").createDirectories().resolve("module-descriptors.jar")
            moduleDescriptorsPath.writeModuleDescriptorsJar(
                "intellij.platform.util.xml" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <module name="intellij.platform.util" namespace="${'$'}legacy_jps_module" visibility="public">
                      <resources>
                        <resource-root path="../lib/util-8.jar"/>
                      </resources>
                    </module>
                """.trimIndent(),
                "jaxb-api.xml" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <module name="jaxb-api" namespace="${'$'}legacy_jps_library" visibility="public">
                      <resources>
                        <resource-root path="../lib/jaxb-api.jar"/>
                      </resources>
                    </module>
                """.trimIndent(),
                "apache.pdfbox3.xml" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <module name="apache.pdfbox3" namespace="${'$'}legacy_jps_library" visibility="public">
                      <resources>
                        <resource-root path="../lib/pdfbox3.jar"/>
                      </resources>
                    </module>
                """.trimIndent(),
                "intellij.platform.boot.xml" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <module name="intellij.platform.boot" visibility="public">
                      <resources>
                        <resource-root path="../lib/platform-loader.jar"/>
                      </resources>
                    </module>
                """.trimIndent(),
                "intellij.platform.lang.xml" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <module name="intellij.platform.lang" namespace="jetbrains" visibility="public">
                      <resources>
                        <resource-root path="../lib/lang.jar"/>
                      </resources>
                    </module>
                """.trimIndent(),
                "plugins/intellij.devkit.xml" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <plugin id="DevKit">
                      <plugin-descriptor-module name="intellij.devkit" namespace="${'$'}legacy_jps_module"/>
                      <module name="intellij.devkit.core" namespace="jetbrains" loading="optional"/>
                    </plugin>
                """.trimIndent()
            )

            val coordinates = loadModuleDescriptorCoordinates(
                moduleDescriptorsPath,
                setOf("lib/util-8.jar", "lib/platform-loader.jar", "lib/pdfbox3.jar"),
            )

            assertTrue(Coordinates("com.jetbrains.intellij.platform", "util") in coordinates)
            assertTrue(Coordinates("com.jetbrains.intellij.platform", "boot") in coordinates)
            assertFalse(Coordinates("com.jetbrains.apache.pdfbox3", "pdfbox3") in coordinates)
            assertFalse(Coordinates("com.jetbrains.intellij.platform", "lang") in coordinates)
            assertFalse(coordinates.any { it.artifactId == "jaxb-api" })
        } finally {
            platformPath.deleteRecursively()
        }
    }

    private fun resources(path: String) = ModuleDescriptor.Resources(
        resourceRoot = ModuleDescriptor.Resources.ResourceRoot(path = path),
    )

    private fun java.nio.file.Path.writeModuleDescriptorsJar(vararg entries: Pair<String, String>) {
        outputStream().use { outputStream ->
            ZipOutputStream(outputStream).use { zip ->
                entries.forEach { (entryName, content) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
        }
    }
}
