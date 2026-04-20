// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.jetbrains.intellij.platform.gradle.utils.ModuleDescriptorsParser
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CollectorTransformerTest {

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `load module descriptors with a slim parser`() {
        val moduleDescriptorsPath = createTempDirectory("module-descriptors")
            .resolve("module-descriptors.jar")

        try {
            moduleDescriptorsPath.writeModuleDescriptorsJar(
                "intellij.platform.util.xml" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <module name="intellij.platform.util" namespace="jps" visibility="public">
                      <dependencies>
                        <module name="intellij.platform.core" namespace="jps"/>
                        <module name="intellij.platform.util.base" visibility="public"/>
                      </dependencies>
                      <resources>
                        <resource-root path="../lib/util.jar"/>
                      </resources>
                    </module>
                """.trimIndent(),
                "legacy.xml" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <module name="jaxb-api" namespace="${'$'}legacy_jps_library" visibility="public">
                      <resources>
                        <resource-root path="../lib/jaxb-api.jar"/>
                      </resources>
                    </module>
                """.trimIndent(),
            )

            val descriptors = ModuleDescriptorsParser.load(moduleDescriptorsPath)
            val util = assertNotNull(descriptors["intellij.platform.util"])
            val legacy = assertNotNull(descriptors["jaxb-api"])

            assertEquals(
                listOf("intellij.platform.core", "intellij.platform.util.base"),
                util.dependencies.map { it.name },
            )
            assertEquals("lib/util.jar", util.path)
            assertEquals(emptyList(), legacy.dependencies)
            assertEquals("lib/jaxb-api.jar", legacy.path)
        } finally {
            moduleDescriptorsPath.parent.deleteRecursively()
        }
    }

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
