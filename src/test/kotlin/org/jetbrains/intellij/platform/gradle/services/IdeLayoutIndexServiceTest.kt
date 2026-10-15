// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import com.jetbrains.plugin.structure.jar.FsHandleFileSystem
import com.jetbrains.plugin.structure.jar.SingletonCachingJarFileSystemProvider
import kotlinx.serialization.encodeToString
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.intellij.platform.gradle.models.json
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IdeLayoutIndexServiceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `indexing preserves JAR filesystems held by plugin readers`() {
        val platformPath = createIde()
        val cacheDirectory = temporaryFolder.newFolder("index").toPath()

        SingletonCachingJarFileSystemProvider.getFileSystem(platformPath.resolve("lib/platform.jar")).use { fileSystem ->
            val descriptor = (fileSystem as FsHandleFileSystem).delegateFileSystem.getPath("META-INF/plugin.xml")
            val originalDescriptor = descriptor.readText()

            val index = service().resolve(platformPath, cacheDirectory)

            assertNotNull(index.findById("com.intellij.java"))
            // The reader still owns its handle, even after the indexer has finished reading the same JAR.
            assertEquals(originalDescriptor, descriptor.readText())
        }
    }

    @Test
    fun `rebuilds incomplete indices from schema 3`() {
        val platformPath = createIde()
        val cacheDirectory = temporaryFolder.newFolder("index").toPath()
        val index = service().resolve(platformPath, cacheDirectory)
        val cacheFile = cacheDirectory.listDirectoryEntries("*.json").single()
        cacheFile.writeText(json.encodeToString(index.copy(schemaVersion = 3, entries = emptyList())))

        val rebuilt = service().resolve(platformPath, cacheDirectory)

        assertNotNull(rebuilt.findById("com.intellij.java"))
        assertEquals(index, rebuilt)
    }

    private fun service() = object : IdeLayoutIndexService() {
        override fun getParameters(): BuildServiceParameters.None = error("This service has no parameters")
    }

    private fun createIde(): Path {
        val platformPath = temporaryFolder.newFolder("idea").toPath()
        platformPath.resolve("build.txt").writeText("IC-243.28141.18")
        platformPath.resolve("product-info.json").writeText(
            """
            {
                "name": "IntelliJ IDEA Community Edition",
                "version": "2024.3",
                "buildNumber": "243.28141.18",
                "productCode": "IC",
                "dataDirectoryName": "IdeaIC2024.3",
                "svgIconPath": "bin/idea.svg",
                "productVendor": "JetBrains",
                "modules": [],
                "bundledPlugins": ["com.intellij.java"],
                "layout": [
                    {
                        "name": "com.intellij.java",
                        "kind": "plugin",
                        "classPath": ["plugins/java/lib/java-impl.jar"]
                    }
                ]
            }
            """.trimIndent(),
        )
        platformPath.resolve("lib/platform.jar").writePluginJar("com.intellij")
        platformPath.resolve("plugins/java/lib/java-impl.jar").writePluginJar("com.intellij.java")
        val modulesDirectory = platformPath.resolve("modules").createDirectories()
        ZipOutputStream(modulesDirectory.resolve("module-descriptors.jar").outputStream()).use { }
        return platformPath
    }

    private fun Path.writePluginJar(id: String) {
        parent.createDirectories()
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/plugin.xml"))
            zip.write(
                """
                <idea-plugin>
                    <id>$id</id>
                    <name>$id</name>
                    <version>1.0</version>
                    <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
        }
    }
}
