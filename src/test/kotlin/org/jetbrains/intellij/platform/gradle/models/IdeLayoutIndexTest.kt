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
    fun `plugins clashing with plugins requiring excluded dependencies remain included`() {
        val index = index(plugin("A"), plugin("B", "plugin:A"), plugin("D"))
        val testPlugins = index.resolveTestPlugins(
            bundledPluginIds = listOf("A", "B", "D"),
            excludes = mapOf("A" to "listed"),
            clashes = mapOf("D" to mapOf("B" to listOf("Z.Z.L"))),
            declaredPlugins = emptyList(),
            requiredPlugins = emptyList(),
        )

        assertEquals(listOf("D"), testPlugins.includedIds)
        assertEquals(listOf("A", "B"), testPlugins.excludedIds)
        assertEquals("requires excluded 'A'", testPlugins.excluded.last().reason)
        assertEquals("A", testPlugins.excluded.last().root?.id)
        assertTrue(testPlugins.clashes.isEmpty())
    }

    @Test
    fun `plugins clashing with plugins requiring modules without surviving owners remain included`() {
        val index = index(plugin("A"), module("M", "A"), plugin("B", "module:M"), plugin("D"))
        val testPlugins = index.resolveTestPlugins(
            bundledPluginIds = listOf("A", "B", "D"),
            excludes = mapOf("A" to "listed"),
            clashes = mapOf("D" to mapOf("B" to listOf("Z.Z.L"))),
            declaredPlugins = emptyList(),
            requiredPlugins = emptyList(),
        )

        assertEquals(listOf("D"), testPlugins.includedIds)
        assertEquals(listOf("A", "B"), testPlugins.excludedIds)
        assertEquals("requires excluded 'M'", testPlugins.excluded.last().reason)
        assertEquals("A", testPlugins.excluded.last().root?.id)
    }

    @Test
    fun `clash exclusions are reconsidered when a later declared plugin excludes the counterpart`() {
        val index = index(plugin("B"), plugin("D"), plugin("P"))
        val testPlugins = index.resolveTestPlugins(
            bundledPluginIds = listOf("B", "D", "P"),
            excludes = emptyMap(),
            clashes = mapOf(
                "D" to mapOf("B" to listOf("Z.Z.L")),
                "P" to mapOf("B" to listOf("Z.Z.Z")),
            ),
            declaredPlugins = listOfNotNull(index.findById("P")),
            requiredPlugins = emptyList(),
        )

        assertEquals(listOf("D", "P"), testPlugins.includedIds)
        assertEquals(listOf("B"), testPlugins.excludedIds)
        assertEquals("1 obfuscated classes clash with 'P'", testPlugins.excluded.single().reason)
        assertEquals(listOf("Z.Z.Z"), testPlugins.excluded.single().clashes)
        assertTrue(testPlugins.clashes.isEmpty())
    }

    @Test
    fun `clash exclusions are reconsidered when other clashes remove all module owners`() {
        val index = index(
            plugin("A"),
            plugin("C"),
            module("M", "A", "C"),
            plugin("B", "module:M"),
            plugin("D"),
        )
        val testPlugins = index.resolveTestPlugins(
            bundledPluginIds = listOf("A", "C", "B", "D"),
            excludes = emptyMap(),
            clashes = mapOf(
                "A" to mapOf(IDEA_CORE to listOf("Z.Z.A")),
                "C" to mapOf(IDEA_CORE to listOf("Z.Z.C")),
                "D" to mapOf("B" to listOf("Z.Z.L")),
            ),
            declaredPlugins = emptyList(),
            requiredPlugins = emptyList(),
        )

        assertEquals(listOf("D"), testPlugins.includedIds)
        assertEquals(listOf("A", "C", "B"), testPlugins.excludedIds)
        assertEquals("requires excluded 'M'", testPlugins.excluded.last().reason)
        assertEquals("A", testPlugins.excluded.last().root?.id)
        assertEquals(listOf(listOf("Z.Z.A"), listOf("Z.Z.C"), emptyList()), testPlugins.excluded.map { it.clashes })
    }

    @Test
    fun `protected plugins do not report clashes with dependency-invalid counterparts`() {
        val index = index(plugin("A"), plugin("B", "plugin:A"), plugin("D"))
        val clashes = mapOf("D" to mapOf("B" to listOf("Z.Z.L")))
        val declared = index.resolveTestPlugins(
            bundledPluginIds = listOf("A", "B", "D"),
            excludes = mapOf("A" to "listed"),
            clashes = clashes,
            declaredPlugins = listOfNotNull(index.findById("D")),
            requiredPlugins = emptyList(),
        )
        val required = index.resolveTestPlugins(
            bundledPluginIds = listOf("A", "B", "D"),
            excludes = mapOf("A" to "listed"),
            clashes = clashes,
            declaredPlugins = emptyList(),
            requiredPlugins = listOfNotNull(index.findById("D")),
        )

        assertEquals(listOf("D"), declared.includedIds)
        assertEquals(listOf("A", "B"), declared.excludedIds)
        assertTrue(declared.clashes.isEmpty())
        assertEquals(listOf("D"), required.includedIds)
        assertEquals(listOf("A", "B"), required.excludedIds)
        assertTrue(required.clashes.isEmpty())
    }

    @Test
    fun `dependency cycles are restored together when their clash counterpart disappears`() {
        val index = index(plugin("B"), plugin("D", "plugin:E"), plugin("E", "plugin:D"), plugin("P"))
        val testPlugins = index.resolveTestPlugins(
            bundledPluginIds = listOf("B", "D", "E", "P"),
            excludes = emptyMap(),
            clashes = mapOf(
                "D" to mapOf("B" to listOf("Z.Z.L")),
                "P" to mapOf("B" to listOf("Z.Z.Z")),
            ),
            declaredPlugins = listOfNotNull(index.findById("P")),
            requiredPlugins = emptyList(),
        )

        assertEquals(listOf("D", "E", "P"), testPlugins.includedIds)
        assertEquals(listOf("B"), testPlugins.excludedIds)
        assertEquals("1 obfuscated classes clash with 'P'", testPlugins.excluded.single().reason)
        assertEquals(listOf("Z.Z.Z"), testPlugins.excluded.single().clashes)
        assertTrue(testPlugins.clashes.isEmpty())
    }

    @Test
    fun `restoration can choose an alternative module owner whose clash counterpart disappears`() {
        val index = index(plugin("X"), plugin("B"), plugin("D", "module:M"), plugin("Y"), module("M", "X", "Y"), plugin("P"))
        val testPlugins = index.resolveTestPlugins(
            bundledPluginIds = listOf("X", "B", "D", "Y", "P"),
            excludes = emptyMap(),
            clashes = mapOf(
                "X" to mapOf(IDEA_CORE to listOf("Z.Z.X")),
                "Y" to mapOf("B" to listOf("Z.Z.L")),
                "P" to mapOf("B" to listOf("Z.Z.Z")),
            ),
            declaredPlugins = listOfNotNull(index.findById("P")),
            requiredPlugins = emptyList(),
        )

        assertEquals(listOf("D", "Y", "P"), testPlugins.includedIds)
        assertEquals(listOf("X", "B"), testPlugins.excludedIds)
        assertEquals(listOf(listOf("Z.Z.X"), listOf("Z.Z.Z")), testPlugins.excluded.map { it.clashes })
        assertTrue(testPlugins.clashes.isEmpty())
    }

    @Test
    fun `a plugin cannot be restored with a dependency that clashes with it`() {
        val index = index(plugin("A", "plugin:B"), plugin("B"))
        val testPlugins = index.resolveTestPlugins(
            bundledPluginIds = listOf("A", "B"),
            excludes = emptyMap(),
            clashes = mapOf("B" to mapOf("A" to listOf("Z.Z.L"))),
            declaredPlugins = emptyList(),
            requiredPlugins = emptyList(),
        )

        assertEquals(listOf("B"), testPlugins.includedIds)
        assertEquals(listOf("A"), testPlugins.excludedIds)
        assertEquals("1 obfuscated classes clash with 'B'", testPlugins.excluded.single().reason)
        assertEquals(listOf("Z.Z.L"), testPlugins.excluded.single().clashes)
        assertTrue(testPlugins.clashes.isEmpty())
    }

    @Test
    fun `mutually dependent clashing plugins remain excluded without oscillating`() {
        val index = index(plugin("A", "plugin:B"), plugin("B", "plugin:A"), plugin("D"))
        val testPlugins = index.resolveTestPlugins(
            bundledPluginIds = listOf("A", "B", "D"),
            excludes = emptyMap(),
            clashes = mapOf("B" to mapOf("A" to listOf("Z.Z.L"))),
            declaredPlugins = emptyList(),
            requiredPlugins = emptyList(),
        )

        assertEquals(listOf("D"), testPlugins.includedIds)
        assertEquals(listOf("B", "A"), testPlugins.excludedIds)
        assertEquals("required plugins have mutually clashing obfuscated classes", testPlugins.excluded.first().reason)
        assertEquals("requires excluded 'B'", testPlugins.excluded.last().reason)
        assertEquals("B", testPlugins.excluded.last().root?.id)
        assertTrue(testPlugins.excluded.all { it.clashes.isEmpty() })
        assertTrue(testPlugins.clashes.isEmpty())
    }

    @Test
    fun `a required shadowed plugin reports its clash without excluding the counterpart`() {
        val index = index(plugin("A"), plugin("B"))
        val testPlugins = index.resolveTestPlugins(
            bundledPluginIds = listOf("A", "B"),
            excludes = emptyMap(),
            clashes = mapOf("B" to mapOf("A" to listOf("Z.Z.L"))),
            declaredPlugins = emptyList(),
            requiredPlugins = listOfNotNull(index.findById("B")),
        )

        assertEquals(listOf("A", "B"), testPlugins.includedIds)
        assertTrue(testPlugins.excluded.isEmpty())
        assertEquals(listOf("B" to false), testPlugins.clashes.map { it.entry.id to it.declared })
        assertEquals(listOf("Z.Z.L"), testPlugins.clashes.single().classes)
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
