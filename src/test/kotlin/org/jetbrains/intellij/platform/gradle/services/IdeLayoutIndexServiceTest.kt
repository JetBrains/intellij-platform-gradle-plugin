// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import com.jetbrains.plugin.structure.jar.FsHandleFileSystem
import com.jetbrains.plugin.structure.jar.SingletonCachingJarFileSystemProvider
import kotlinx.serialization.encodeToString
import org.gradle.api.services.BuildServiceParameters
import org.intellij.lang.annotations.Language
import org.jetbrains.intellij.platform.gradle.models.IdeLayoutIndex
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

    @Test
    fun `record plugins declaring content module as its owners`() {
        val index = createIndex()

        assertEquals(listOf("intellij.structureView.plugin"), index.ownerIdsOf("intellij.platform.structureView"))
        assertEquals(listOf("com.intellij"), index.ownerIdsOf("intellij.spellchecker"))
        assertEquals(listOf("JavaScript", "com.intellij.kubernetes"), index.ownerIdsOf("intellij.platform.commercial.verifier"))
        assertEquals(emptyList(), requireNotNull(index.findById("intellij.structureView.plugin")).owners)
    }

    @Test
    fun `find missing plugin declaring bundled module present on classpath`() {
        val owners = createIndex().findMissingContentModuleOwners(
            setOf("plugins/structureView/lib/modules/intellij.platform.structureView.jar"),
        )

        assertEquals(listOf("intellij.structureView.plugin"), owners.map { it.id })
    }

    @Test
    fun `find missing plugin when several of its content modules are present on classpath`() {
        val owners = createIndex().findMissingContentModuleOwners(
            setOf(
                "plugins/structureView/lib/modules/intellij.platform.structureView.jar",
                "plugins/structureView/lib/modules/intellij.platform.structureView.impl.jar",
            ),
        )

        assertEquals(listOf("intellij.structureView.plugin"), owners.map { it.id })
    }

    @Test
    fun `skip bundled module whose owner is present on classpath`() {
        val owners = createIndex().findMissingContentModuleOwners(
            setOf(
                "plugins/structureView/lib/structureView.jar",
                "plugins/structureView/lib/modules/intellij.platform.structureView.jar",
                "plugins/kubernetes/lib/kubernetes.jar",
                "plugins/javascript/lib/modules/intellij.platform.commercial.verifier.jar",
            ),
        )

        assertEquals(emptyList(), owners)
    }

    @Test
    fun `skip bundled module owned by the core`() {
        val owners = createIndex().findMissingContentModuleOwners(setOf("lib/intellij.spellchecker.jar"))

        assertEquals(emptyList(), owners)
    }

    @Test
    fun `pick the first plugin declaring bundled module declared by several plugins`() {
        val owners = createIndex().findMissingContentModuleOwners(
            setOf("plugins/javascript/lib/modules/intellij.platform.commercial.verifier.jar"),
        )

        assertEquals(listOf("JavaScript"), owners.map { it.id })
    }

    @Test
    fun `pick the plugin declaring modules requiring bundled module declared by several plugins`() {
        val owners = createIndex().findMissingContentModuleOwners(
            setOf(
                "plugins/javascript/lib/modules/intellij.platform.commercial.verifier.jar",
                "plugins/kubernetes/lib/modules/intellij.kubernetes.core.jar",
            ),
        )

        assertEquals(listOf("com.intellij.kubernetes"), owners.map { it.id })
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

    private fun IdeLayoutIndex.ownerIdsOf(id: String) = requireNotNull(findById(id))
        .owners
        .map { requireNotNull(findByKey(it)).id }

    private fun createIndex(): IdeLayoutIndex {
        val platformPath = temporaryFolder.newFolder("platform").toPath()

        platformPath.resolve("build.txt").writeText("IU-262.9437.185")
        platformPath.resolve("product-info.json").writeText(
            //language=JSON
            """
            {
              "name": "IntelliJ IDEA",
              "version": "2026.2.1",
              "buildNumber": "262.9437.185",
              "productCode": "IU",
              "dataDirectoryName": "IntelliJIdea2026.2",
              "svgIconPath": "bin/idea.svg",
              "productVendor": "JetBrains",
              "launch": [],
              "bundledPlugins": [],
              "modules": [],
              "layout": [
                { "name": "intellij.spellchecker", "kind": "productModuleV2", "classPath": ["lib/intellij.spellchecker.jar"] },
                { "name": "JavaScript", "kind": "plugin", "classPath": ["plugins/javascript/lib/javascript.jar"] },
                { "name": "com.intellij.kubernetes", "kind": "plugin", "classPath": ["plugins/kubernetes/lib/kubernetes.jar"] },
                {
                  "name": "intellij.platform.commercial.verifier",
                  "kind": "moduleV2",
                  "classPath": ["plugins/javascript/lib/modules/intellij.platform.commercial.verifier.jar"]
                },
                {
                  "name": "intellij.kubernetes.core",
                  "kind": "moduleV2",
                  "classPath": ["plugins/kubernetes/lib/modules/intellij.kubernetes.core.jar"]
                },
                {
                  "name": "intellij.platform.structureView",
                  "kind": "moduleV2",
                  "classPath": ["plugins/structureView/lib/modules/intellij.platform.structureView.jar"]
                },
                {
                  "name": "intellij.platform.structureView.impl",
                  "kind": "moduleV2",
                  "classPath": ["plugins/structureView/lib/modules/intellij.platform.structureView.impl.jar"]
                },
                {
                  "name": "intellij.structureView.plugin",
                  "kind": "plugin",
                  "classPath": [
                    "plugins/structureView/lib/structureView.jar",
                    "plugins/structureView/lib/modules/intellij.platform.structureView.jar",
                    "plugins/structureView/lib/modules/intellij.platform.structureView.impl.jar"
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        platformPath.writeJar(
            "modules/module-descriptors.jar",
            "intellij.platform.structureView.xml" to moduleDescriptor(
                name = "intellij.platform.structureView",
                resourceRoot = "../plugins/structureView/lib/modules/intellij.platform.structureView.jar",
            ),
            "intellij.platform.structureView.impl.xml" to moduleDescriptor(
                name = "intellij.platform.structureView.impl",
                resourceRoot = "../plugins/structureView/lib/modules/intellij.platform.structureView.impl.jar",
            ),
            "intellij.spellchecker.xml" to moduleDescriptor(
                name = "intellij.spellchecker",
                resourceRoot = "../lib/intellij.spellchecker.jar",
            ),
            "intellij.platform.commercial.verifier.xml" to moduleDescriptor(
                name = "intellij.platform.commercial.verifier",
                resourceRoot = "../plugins/javascript/lib/modules/intellij.platform.commercial.verifier.jar",
            ),
            "intellij.kubernetes.core.xml" to moduleDescriptor(
                name = "intellij.kubernetes.core",
                resourceRoot = "../plugins/kubernetes/lib/modules/intellij.kubernetes.core.jar",
            ),
        )
        platformPath.writeJar(
            "lib/app.jar",
            "META-INF/plugin.xml" to pluginXml(
                id = "com.intellij",
                content = """<module name="intellij.spellchecker" />""",
            ),
        )
        platformPath.writeJar(
            "lib/intellij.spellchecker.jar",
            "intellij.spellchecker.xml" to "<idea-plugin />",
        )
        platformPath.writeJar(
            "plugins/structureView/lib/structureView.jar",
            "META-INF/plugin.xml" to pluginXml(
                id = "intellij.structureView.plugin",
                content = """
                    <module name="intellij.platform.structureView" loading="required" />
                    <module name="intellij.platform.structureView.impl" loading="required" />
                """.trimIndent(),
            ),
        )
        platformPath.writeJar(
            "plugins/structureView/lib/modules/intellij.platform.structureView.jar",
            "intellij.platform.structureView.xml" to "<idea-plugin />",
        )
        platformPath.writeJar(
            "plugins/structureView/lib/modules/intellij.platform.structureView.impl.jar",
            "intellij.platform.structureView.impl.xml" to "<idea-plugin />",
        )
        val kubernetesCore =
            """<idea-plugin><dependencies><module name="intellij.platform.commercial.verifier" /></dependencies></idea-plugin>"""
        val commercialVerifier =
            """<module name="intellij.platform.commercial.verifier" loading="optional"><![CDATA[<idea-plugin visibility="private" />]]></module>"""
        platformPath.writeJar(
            "plugins/javascript/lib/javascript.jar",
            "META-INF/plugin.xml" to pluginXml(id = "JavaScript", content = commercialVerifier),
        )
        platformPath.writeJar(
            "plugins/javascript/lib/modules/intellij.platform.commercial.verifier.jar",
            "intellij.platform.commercial.verifier.xml" to """<idea-plugin visibility="private" />""",
        )
        platformPath.writeJar(
            "plugins/kubernetes/lib/kubernetes.jar",
            "META-INF/plugin.xml" to pluginXml(
                id = "com.intellij.kubernetes",
                content = commercialVerifier + """<module name="intellij.kubernetes.core" loading="required" />""",
            ),
        )
        platformPath.writeJar(
            "plugins/kubernetes/lib/modules/intellij.kubernetes.core.jar",
            "intellij.kubernetes.core.xml" to kubernetesCore,
        )

        return service().resolve(platformPath, platformPath.resolve("layoutIndex"))
    }

    private fun moduleDescriptor(name: String, resourceRoot: String) =
        //language=XML
        """
        <module name="$name">
          <resources>
            <resource-root path="$resourceRoot" />
          </resources>
        </module>
        """.trimIndent()

    private fun pluginXml(id: String, @Language("XML") content: String = "") =
        //language=XML
        """
        <idea-plugin>
          <id>$id</id>
          <name>$id</name>
          <vendor>JetBrains</vendor>
          <content>
            $content
          </content>
        </idea-plugin>
        """.trimIndent()

    private fun Path.writeJar(path: String, vararg entries: Pair<String, String>) {
        val jar = resolve(path)
        jar.parent.createDirectories()
        ZipOutputStream(jar.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
