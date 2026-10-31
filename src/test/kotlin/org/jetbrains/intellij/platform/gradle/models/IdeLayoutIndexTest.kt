// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import org.jetbrains.intellij.platform.gradle.Constants.IDEA_CORE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IdeLayoutIndexTest {

    @Test
    fun `preserves first matching ids and module aliases`() {
        val first = IdeLayoutIndex.Entry(
            key = "entry-0",
            id = "com.intellij.example",
            definedModules = listOf("com.intellij.example", "example.alias"),
        )
        val second = IdeLayoutIndex.Entry(
            key = "entry-1",
            id = "com.intellij.example",
            definedModules = listOf("example.alias", "example.alias.2"),
        )
        val third = IdeLayoutIndex.Entry(
            key = "entry-2",
            id = "com.intellij.example.module",
            isModule = true,
            definedModules = listOf("com.intellij.example.module"),
        )

        val ideLayoutIndex = IdeLayoutIndex(
            fullVersion = "IU-999.0",
            entries = listOf(first, second, third),
        )

        assertSame(first, ideLayoutIndex.findById("com.intellij.example"))
        assertSame(first, ideLayoutIndex.findByModuleId("example.alias"))
        assertSame(first, ideLayoutIndex.findByIdOrModuleId("example.alias"))
        assertSame(second, ideLayoutIndex.findByModuleId("example.alias.2"))
        assertSame(second, ideLayoutIndex.findByIdOrModuleId("example.alias.2"))
        assertSame(third, ideLayoutIndex.findByKey("entry-2"))
    }

    @Test
    fun `bundled entry views split plugins and modules`() {
        val plugin = IdeLayoutIndex.Entry(
            key = "entry-0",
            id = "com.intellij.example",
        )
        val module = IdeLayoutIndex.Entry(
            key = "entry-1",
            id = "com.intellij.example.module",
            isModule = true,
        )

        val ideLayoutIndex = IdeLayoutIndex(
            fullVersion = "IU-999.0",
            entries = listOf(plugin, module),
        )

        assertEquals(listOf(plugin), ideLayoutIndex.bundledPlugins.toList())
        assertEquals(listOf(module), ideLayoutIndex.bundledModules.toList())
    }

    private fun plugin(id: String, vararg dependencies: String, classpath: List<String> = listOf("plugins/$id/lib/$id.jar")) =
        IdeLayoutIndex.Entry(
            key = "plugin:$id",
            id = id,
            classpath = classpath,
            dependencies = dependencies.toList(),
            definedModules = listOf(id),
        )

    private fun module(id: String, vararg owners: String, classpath: List<String> = listOf("plugins/${owners.first()}/lib/modules/$id.jar")) =
        IdeLayoutIndex.Entry(
            key = "module:$id",
            id = id,
            isModule = true,
            classpath = classpath,
            definedModules = listOf(id),
            owners = owners.map { "plugin:$it" },
        )

    private fun index(vararg entries: IdeLayoutIndex.Entry) = IdeLayoutIndex(fullVersion = "IU-262.1", entries = entries.toList())

    private val cascadeIndex = index(
        plugin("A", classpath = listOf("plugins/A/lib/A.jar", "plugins/A/lib/modules/mA.jar")),
        module("mA", "A"),
        plugin("B", "plugin:A"),
        plugin("C", "module:mA"),
        plugin("D", classpath = listOf("plugins/D/lib/D.jar", "plugins/A/lib/modules/mShared.jar")),
        module("mShared", "A", "D"),
        plugin("E", "module:mShared"),
    )

    private val IdeLayoutIndex.TestPlugins.includedIds
        get() = included.map { it.id }

    private val IdeLayoutIndex.TestPlugins.excludedIds
        get() = excluded.map { it.entry.id }

    @Test
    fun `exclusions cascade to plugins requiring excluded plugins or modules`() {
        val testPlugins = cascadeIndex.resolveTestPlugins(
            bundledPluginIds = listOf("A", "B", "C", "D", "E"),
            excludes = mapOf("A" to "listed"),
            clashes = emptyMap(),
            declaredPlugins = emptyList(),
            requiredPlugins = emptyList(),
        )

        assertEquals(listOf("D", "E"), testPlugins.includedIds)
        assertEquals(listOf("A", "B", "C"), testPlugins.excludedIds)
        assertEquals("listed", testPlugins.excluded.first().reason)
        assertEquals(listOf("A", "A"), testPlugins.excluded.drop(1).map { it.root?.id })
        assertTrue(testPlugins.excluded.drop(1).all { it.reason.startsWith("requires excluded ") })
        assertTrue(testPlugins.kept.isEmpty())
    }

    @Test
    fun `declared and required plugins with their dependencies are never excluded`() {
        val declared = cascadeIndex.resolveTestPlugins(
            bundledPluginIds = listOf("A", "B", "C", "D", "E"),
            excludes = mapOf("A" to "listed"),
            clashes = emptyMap(),
            declaredPlugins = listOfNotNull(cascadeIndex.findById("B")),
            requiredPlugins = emptyList(),
        )

        assertEquals(listOf("A", "B", "C", "D", "E"), declared.includedIds)
        assertEquals(listOf("A"), declared.kept.map { it.entry.id })
        assertTrue(declared.excluded.isEmpty())

        val required = cascadeIndex.resolveTestPlugins(
            bundledPluginIds = listOf("A", "B", "C", "D", "E"),
            excludes = mapOf("A" to "listed"),
            clashes = emptyMap(),
            declaredPlugins = emptyList(),
            requiredPlugins = listOfNotNull(cascadeIndex.findByIdOrModuleId("mA")),
        )

        assertEquals(listOf("A"), required.kept.map { it.entry.id })
        assertTrue(required.excluded.isEmpty())
    }

    @Test
    fun `plugins clashing with the core are excluded with their dependents unless declared`() {
        val index = index(plugin("U"), plugin("V", "plugin:U"), plugin("W"))
        val clashes = mapOf("U" to mapOf(IDEA_CORE to listOf("Z.Z.L", "Z.Z.Z")))

        val undeclared = index.resolveTestPlugins(listOf("U", "V", "W"), emptyMap(), clashes, emptyList(), emptyList())

        assertEquals(listOf("W"), undeclared.includedIds)
        assertEquals(listOf("U", "V"), undeclared.excludedIds)
        assertEquals(listOf("Z.Z.L", "Z.Z.Z"), undeclared.excluded.first().clashes)
        assertEquals("2 obfuscated classes clash with the IntelliJ Platform", undeclared.excluded.first().reason)
        assertEquals("U", undeclared.excluded.last().root?.id)

        val declared = index.resolveTestPlugins(listOf("U", "V", "W"), emptyMap(), clashes, listOfNotNull(index.findById("U")), emptyList())

        assertEquals(listOf("U", "V", "W"), declared.includedIds)
        assertEquals(listOf("U" to true), declared.clashes.map { it.entry.id to it.declared })

        val required = index.resolveTestPlugins(listOf("U", "V", "W"), emptyMap(), clashes, emptyList(), listOfNotNull(index.findById("V")))

        assertEquals(listOf("U", "V", "W"), required.includedIds)
        assertEquals(listOf("U" to false), required.clashes.map { it.entry.id to it.declared })
    }

    @Test
    fun `the shadowed plugin of clashing plugins is excluded`() {
        val index = index(plugin("P1"), plugin("P2"))
        val clashes = mapOf("P2" to mapOf("P1" to listOf("a.b.C")))

        val undeclared = index.resolveTestPlugins(listOf("P1", "P2"), emptyMap(), clashes, emptyList(), emptyList())
        assertEquals(listOf("P1"), undeclared.includedIds)
        assertEquals("1 obfuscated classes clash with 'P1'", undeclared.excluded.single().reason)

        val declared = index.resolveTestPlugins(listOf("P1", "P2"), emptyMap(), clashes, listOfNotNull(index.findById("P2")), emptyList())
        assertEquals(listOf("P2"), declared.includedIds)
        assertEquals("1 obfuscated classes clash with 'P2'", declared.excluded.single().reason)

        val counterpartExcluded = index.resolveTestPlugins(listOf("P1", "P2"), mapOf("P1" to "listed"), clashes, emptyList(), emptyList())
        assertEquals(listOf("P2"), counterpartExcluded.includedIds)
    }

    @Test
    fun `a module declared by several plugins resolves to a single present owner`() {
        val index = index(
            plugin("X", classpath = listOf("plugins/X/lib/X.jar", "plugins/X/lib/modules/M.jar")),
            plugin("Y", classpath = listOf("plugins/Y/lib/Y.jar", "plugins/X/lib/modules/M.jar")),
            module("M", "X", "Y"),
        )

        assertEquals(listOf("Y"), index.findPresentPlugins(setOf("plugins/Y/lib/Y.jar", "plugins/X/lib/modules/M.jar")).map { it.id })
        assertEquals(listOf("X"), index.findPresentPlugins(setOf("plugins/X/lib/modules/M.jar")).map { it.id })
    }

    @Test
    fun `runtime classpath contains only the JARs listed in the layout`() {
        val index = index(
            plugin(
                "remote",
                classpath = listOf(
                    "plugins/remote/lib/remote.jar",
                    "plugins/remote/lib/modules/intellij.vcs.git.frontend.split.jar",
                    "lib/util.jar",
                ),
            ),
            plugin(
                "unlisted",
                classpath = listOf(
                    "plugins/unlisted/lib/unlisted.jar",
                    "plugins/unlisted/lib/modules/content.jar",
                    "plugins/unlisted/lib/modules/other.jar",
                    "plugins/unlisted/lib/frontend-split/client.jar",
                ),
            ),
            module("content", "unlisted"),
        )
        val layout = IdeLayoutIndex.Layout(
            ProductInfo(
                layout = listOf(
                    ProductInfo.LayoutItem("remote", ProductInfo.LayoutItemKind.plugin, listOf("plugins/remote/lib/remote.jar")),
                ),
            ),
        )

        assertEquals(
            listOf("plugins/remote/lib/remote.jar", "lib/util.jar"),
            index.runtimeClasspath(requireNotNull(index.findById("remote")), layout),
        )
        assertEquals(
            listOf("plugins/unlisted/lib/unlisted.jar", "plugins/unlisted/lib/modules/content.jar"),
            index.runtimeClasspath(requireNotNull(index.findById("unlisted")), layout),
        )
    }
}
