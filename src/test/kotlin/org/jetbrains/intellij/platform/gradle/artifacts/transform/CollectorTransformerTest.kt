// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.jetbrains.intellij.platform.gradle.utils.ModuleDescriptorsParser
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CollectorTransformerTest {

    /**
     * Regression test for JetBrains/intellij-platform-gradle-plugin#2033.
     *
     * The plugin public API source JARs bundled in `lib/src` must be collected separately from the compile/runtime JARs
     * located in `lib` and `lib/modules`, so they can be surfaced only as sources and never end up on the classpath.
     */
    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `collect bundled plugin sources separately from classpath jars`() {
        val pluginPath = createTempDirectory("plugin")

        try {
            val libPath = pluginPath.resolve("lib").createDirectories()
            val libModulesPath = libPath.resolve("modules").createDirectories()
            val libSrcPath = libPath.resolve("src").createDirectories()

            libPath.resolve("plugin.jar").createFile()
            libModulesPath.resolve("plugin.module.jar").createFile()
            libSrcPath.resolve("plugin-openapi-src.jar").createFile()
            libSrcPath.resolve("plugin-extra-src.jar").createFile()
            // A non-JAR file within lib/src must be ignored.
            libSrcPath.resolve("README.txt").createFile()

            val classpathJars = CollectorTransformer.collectJars(pluginPath).map { it.name }.sorted()
            val sourceJars = CollectorTransformer.collectSourceJars(pluginPath).map { it.name }.sorted()

            assertEquals(listOf("plugin.jar", "plugin.module.jar"), classpathJars)
            assertTrue("lib/src JARs must not be collected as classpath jars") {
                classpathJars.none { it.endsWith("-src.jar") }
            }
            assertEquals(listOf("plugin-extra-src.jar", "plugin-openapi-src.jar"), sourceJars)
        } finally {
            pluginPath.deleteRecursively()
        }
    }

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
            assertEquals("jps", util.namespace)
            assertEquals("lib/util.jar", util.path)
            assertEquals(emptyList(), legacy.dependencies)
            assertEquals("${'$'}legacy_jps_library", legacy.namespace)
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
