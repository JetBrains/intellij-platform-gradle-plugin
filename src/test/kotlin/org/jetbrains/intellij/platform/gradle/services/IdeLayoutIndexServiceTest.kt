// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import com.jetbrains.plugin.structure.jar.FsHandleFileSystem
import com.jetbrains.plugin.structure.jar.SingletonCachingJarFileSystemProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.services.BuildServiceParameters
import org.intellij.lang.annotations.Language
import org.jetbrains.intellij.platform.gradle.Constants.IDEA_CORE
import org.jetbrains.intellij.platform.gradle.models.IdeLayoutIndex
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.json
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.setLastModifiedTime
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    fun `find plugins present on classpath and plugins declaring modules present on it`() {
        val plugins = createIndex().findPresentPlugins(
            setOf(
                "plugins/kubernetes/lib/kubernetes.jar",
                "plugins/structureView/lib/modules/intellij.platform.structureView.jar",
                "lib/intellij.spellchecker.jar",
            ),
        )

        assertEquals(listOf("com.intellij.kubernetes", "intellij.structureView.plugin"), plugins.map { it.id }.sorted())
    }

    @Test
    fun `resolve all bundled plugins for tests`() {
        val testPlugins = createIndex().resolveTestPlugins(bundledPluginIds)

        assertEquals(bundledPluginIds, testPlugins.included.map { it.id })
        assertEquals(emptyList(), testPlugins.excluded)
    }

    @Test
    fun `exclude plugins requiring excluded plugin transitively`() {
        val testPlugins = createIndex().resolveTestPlugins(
            bundledPluginIds,
            excludes = mapOf("intellij.structureView.plugin" to "listed"),
        )

        assertEquals(listOf("JavaScript", "com.intellij.kubernetes"), testPlugins.included.map { it.id })
        assertEquals(listOf("intellij.structureView.plugin", "intellij.libraries.misc.plugin"), testPlugins.excluded.map { it.entry.id })
        val cascaded = testPlugins.excluded.single { it.entry.id == "intellij.libraries.misc.plugin" }
        assertEquals("requires excluded 'intellij.structureView.plugin'", cascaded.reason)
        assertEquals("intellij.structureView.plugin", cascaded.root?.id)
    }

    @Test
    fun `keep plugins requiring module declared by several plugins when one of them is excluded`() {
        val testPlugins = createIndex().resolveTestPlugins(bundledPluginIds, excludes = mapOf("JavaScript" to "listed"))

        assertEquals(
            listOf("intellij.libraries.misc.plugin", "intellij.structureView.plugin", "com.intellij.kubernetes"),
            testPlugins.included.map { it.id },
        )
        assertEquals(listOf("JavaScript"), testPlugins.excluded.map { it.entry.id })
    }

    @Test
    fun `never exclude declared plugins and their dependencies`() {
        val index = createIndex()
        val testPlugins = index.resolveTestPlugins(
            bundledPluginIds,
            excludes = mapOf("intellij.structureView.plugin" to "listed"),
            declaredPlugins = listOfNotNull(index.findById("intellij.libraries.misc.plugin")),
        )

        assertEquals(bundledPluginIds, testPlugins.included.map { it.id })
        assertEquals(listOf("intellij.structureView.plugin"), testPlugins.kept.map { it.entry.id })
    }

    @Test
    fun `never exclude plugins required by the tested plugin`() {
        val index = createIndex()
        val testPlugins = index.resolveTestPlugins(
            bundledPluginIds,
            excludes = mapOf("JavaScript" to "listed"),
            requiredPlugins = listOfNotNull(index.findByIdOrModuleId("JavaScript")),
        )

        assertEquals(bundledPluginIds, testPlugins.included.map { it.id })
        assertEquals(listOf("JavaScript"), testPlugins.kept.map { it.entry.id })
    }

    @Test
    fun `exclude undeclared plugin with clashing classes along with plugins requiring it`() {
        val testPlugins = createIndex().resolveTestPlugins(
            bundledPluginIds,
            clashes = mapOf("intellij.structureView.plugin" to mapOf(IDEA_CORE to listOf("Z.Z.L"))),
        )

        assertEquals(listOf("JavaScript", "com.intellij.kubernetes"), testPlugins.included.map { it.id })
        assertEquals(listOf("Z.Z.L"), testPlugins.excluded.single { it.entry.id == "intellij.structureView.plugin" }.clashes)
        assertEquals(
            "intellij.structureView.plugin",
            testPlugins.excluded.single { it.entry.id == "intellij.libraries.misc.plugin" }.root?.id,
        )
        assertEquals(emptyList(), testPlugins.clashes)
    }

    @Test
    fun `keep declared plugin with clashing classes`() {
        val index = createIndex()
        val testPlugins = index.resolveTestPlugins(
            bundledPluginIds,
            clashes = mapOf("intellij.structureView.plugin" to mapOf(IDEA_CORE to listOf("Z.Z.L"))),
            declaredPlugins = listOfNotNull(index.findById("intellij.structureView.plugin")),
        )

        assertEquals(bundledPluginIds, testPlugins.included.map { it.id })
        assertEquals(emptyList(), testPlugins.excluded)
        val clash = testPlugins.clashes.single()
        assertEquals("intellij.structureView.plugin", clash.entry.id)
        assertTrue(clash.declared)
    }

    @Test
    fun `runtime classpath contains JARs listed in layout or loaded from the plugin directory`() {
        val layout = IdeLayoutIndex.Layout(
            ProductInfo(
                layout = listOf(
                    ProductInfo.LayoutItem("p", ProductInfo.LayoutItemKind.plugin, listOf("plugins/p/lib/p.jar")),
                    ProductInfo.LayoutItem("p.module", ProductInfo.LayoutItemKind.moduleV2, listOf("plugins/p/lib/modules/p.module.jar")),
                ),
            ),
        )
        val index = IdeLayoutIndex(fullVersion = "IU-262.1", entries = emptyList())
        val listed = IdeLayoutIndex.Entry(
            key = "entry-0",
            id = "p",
            definedModules = listOf("p", "p.module"),
            classpath = listOf(
                "lib/core.jar",
                "plugins/p/lib/p.jar",
                "plugins/p/lib/modules/p.module.jar",
                "plugins/p/lib/modules/intellij.p.frontend.split.jar",
                "plugins/p/lib/frontend-split/p-frontend.jar",
            ),
        )
        val unlisted = IdeLayoutIndex.Entry(
            key = "entry-1",
            id = "q",
            definedModules = listOf("q", "q.module"),
            classpath = listOf(
                "plugins/q/lib/q.jar",
                "plugins/q/lib/modules/q.module.jar",
                "plugins/q/lib/modules/intellij.q.frontend.split.jar",
                "plugins/q/lib/frontend-split/q-frontend.jar",
            ),
        )

        assertEquals(
            listOf("lib/core.jar", "plugins/p/lib/p.jar", "plugins/p/lib/modules/p.module.jar"),
            index.runtimeClasspath(listed, layout),
        )
        assertEquals(listOf("plugins/q/lib/q.jar", "plugins/q/lib/modules/q.module.jar"), index.runtimeClasspath(unlisted, layout))
    }

    @Test
    fun `find obfuscated classes of bundled plugins clashing with the core`() {
        val platformPath = createIde()
        val cacheDirectory = temporaryFolder.newFolder("index").toPath()
        platformPath.writeJar(
            "lib/core.jar",
            "Z/Z/L.class" to "core",
            "Z/Z/M.class" to "same",
            "com/example/Util.class" to "core",
        )
        platformPath.writeJar(
            "plugins/java/lib/java-impl.jar",
            "META-INF/plugin.xml" to pluginXml(id = "com.intellij.java"),
            "Z/Z/L.class" to "plugin",
            "Z/Z/M.class" to "same",
            "com/example/Util.class" to "plugin",
        )

        val clashes = service().resolveClassClashes(platformPath, cacheDirectory)

        assertEquals(mapOf("com.intellij.java" to mapOf(IDEA_CORE to listOf("Z.Z.L"))), clashes)
        assertEquals(1, cacheDirectory.listDirectoryEntries("*-class-clashes.json").size)
        assertEquals(clashes, service().resolveClassClashes(platformPath, cacheDirectory))
    }

    @Test
    fun `record every conflicting owner even when earlier owners are excluded`() {
        val platformPath = createIde()
        val cacheDirectory = temporaryFolder.newFolder("index").toPath()
        val pluginIds = listOf("com.intellij.java", "second", "third", "fourth")
        val productInfoFile = platformPath.resolve("product-info.json")
        val productInfo = json.decodeFromString<ProductInfo>(productInfoFile.readText())
        productInfoFile.writeText(
            Json(json) { encodeDefaults = true }.encodeToString(
                productInfo.copy(
                    bundledPlugins = pluginIds,
                    layout = pluginIds.map { id ->
                        ProductInfo.LayoutItem(id, ProductInfo.LayoutItemKind.plugin, listOf("plugins/$id/lib/plugin.jar"))
                    },
                ),
            ),
        )
        pluginIds.zip(listOf("same", "same", "different", "another")).forEach { (id, contents) ->
            platformPath.writeJar(
                "plugins/$id/lib/plugin.jar",
                "META-INF/plugin.xml" to pluginXml(id),
                "Z/Z/L.class" to contents,
            )
        }
        val service = service()
        val clashes = service.resolveClassClashes(platformPath, cacheDirectory)

        assertEquals(
            mapOf(
                "third" to mapOf("com.intellij.java" to listOf("Z.Z.L"), "second" to listOf("Z.Z.L")),
                "fourth" to mapOf(
                    "com.intellij.java" to listOf("Z.Z.L"),
                    "second" to listOf("Z.Z.L"),
                    "third" to listOf("Z.Z.L"),
                ),
            ),
            clashes,
        )
        val resolved = service.resolve(platformPath, cacheDirectory).resolveTestPlugins(
            pluginIds,
            excludes = mapOf("com.intellij.java" to "listed"),
            clashes = clashes,
        )
        assertEquals(listOf("second"), resolved.included.map { it.id })
    }

    @Test
    fun `invalidate memory and disk clash caches when JAR contents change without metadata changes`() {
        val platformPath = createIde()
        val cacheDirectory = temporaryFolder.newFolder("index").toPath()
        val pluginJar = "plugins/java/lib/java-impl.jar"
        fun replaceJar(path: String, vararg entries: Pair<String, String>) {
            platformPath.writeJar("$path.replacement", *entries)
            val jar = platformPath.resolve(path)
            if (Files.exists(jar)) {
                val delegate = SingletonCachingJarFileSystemProvider.getFileSystem(jar).use { fileSystem ->
                    val handle = fileSystem as FsHandleFileSystem
                    // Release this fixture's cached filesystem so Windows can replace the JAR.
                    handle.onCacheRemoval()
                    handle.delegateFileSystem
                }
                assertFalse(delegate.isOpen, "The cached JAR filesystem must be closed before replacing $path on Windows")
            }
            Files.move(platformPath.resolve("$path.replacement"), platformPath.resolve(path), ATOMIC_MOVE, REPLACE_EXISTING)
        }
        fun writePlugin(contents: String) = replaceJar(
            pluginJar,
            "META-INF/plugin.xml" to pluginXml("com.intellij.java"),
            "Z/Z/L.class" to contents,
        )
        replaceJar("lib/core.jar", "Z/Z/L.class" to "aaaa")
        writePlugin("aaaa")
        val service = service()
        assertEquals(emptyMap(), service.resolveClassClashes(platformPath, cacheDirectory))
        val jar = platformPath.resolve(pluginJar)
        val timestamp = jar.getLastModifiedTime()
        val size = jar.fileSize()

        writePlugin("bbbb")
        jar.setLastModifiedTime(timestamp)
        assertEquals(size, jar.fileSize())
        val expected = mapOf("com.intellij.java" to mapOf(IDEA_CORE to listOf("Z.Z.L")))
        assertEquals(expected, service().resolveClassClashes(platformPath, cacheDirectory))
        assertEquals(expected, service.resolveClassClashes(platformPath, cacheDirectory))

        val coreJar = platformPath.resolve("lib/core.jar")
        val coreTimestamp = coreJar.getLastModifiedTime()
        replaceJar("lib/core.jar", "Z/Z/L.class" to "bbbb")
        coreJar.setLastModifiedTime(coreTimestamp)
        assertEquals(emptyMap(), service().resolveClassClashes(platformPath, cacheDirectory))
        assertEquals(emptyMap(), service.resolveClassClashes(platformPath, cacheDirectory))
    }

    private val bundledPluginIds = listOf(
        "intellij.libraries.misc.plugin",
        "intellij.structureView.plugin",
        "JavaScript",
        "com.intellij.kubernetes",
    )

    private fun IdeLayoutIndex.resolveTestPlugins(
        bundledPluginIds: List<String>,
        excludes: Map<String, String> = emptyMap(),
        clashes: Map<String, Map<String, List<String>>> = emptyMap(),
        declaredPlugins: List<IdeLayoutIndex.Entry> = emptyList(),
        requiredPlugins: List<IdeLayoutIndex.Entry> = emptyList(),
    ) = resolveTestPlugins(bundledPluginIds, excludes, clashes, declaredPlugins, requiredPlugins)

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
                {
                  "name": "intellij.libraries.lucene.common",
                  "kind": "moduleV2",
                  "classPath": ["plugins/misc/lib/modules/intellij.libraries.lucene.common.jar"]
                },
                {
                  "name": "intellij.libraries.misc.plugin",
                  "kind": "plugin",
                  "classPath": ["plugins/misc/lib/misc.jar", "plugins/misc/lib/modules/intellij.libraries.lucene.common.jar"]
                },
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
            "intellij.libraries.lucene.common.xml" to moduleDescriptor(
                name = "intellij.libraries.lucene.common",
                resourceRoot = "../plugins/misc/lib/modules/intellij.libraries.lucene.common.jar",
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
            "intellij.spellchecker.xml" to
                """<idea-plugin><dependencies><module name="intellij.libraries.lucene.common" /></dependencies></idea-plugin>""",
        )
        platformPath.writeJar(
            "plugins/misc/lib/misc.jar",
            "META-INF/plugin.xml" to pluginXml(
                id = "intellij.libraries.misc.plugin",
                content = """<module name="intellij.libraries.lucene.common" />""",
                dependencies = """<plugin id="intellij.structureView.plugin" />""",
            ),
        )
        platformPath.writeJar(
            "plugins/misc/lib/modules/intellij.libraries.lucene.common.jar",
            "intellij.libraries.lucene.common.xml" to "<idea-plugin />",
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

    private fun pluginXml(id: String, @Language("XML") content: String = "", @Language("XML") dependencies: String = "") =
        //language=XML
        """
        <idea-plugin>
          <id>$id</id>
          <name>$id</name>
          <vendor>JetBrains</vendor>
          <dependencies>
            $dependencies
          </dependencies>
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
