// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.sources

import org.jetbrains.intellij.platform.gradle.models.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*
import kotlin.test.*

class IntelliJPlatformSourceResolverTest {
    @get:Rule val temporary = TemporaryFolder()
    private val root get() = temporary.root.toPath()
    private val cache get() = root.resolve("cache with spaces")
    private val resolver get() = IntelliJPlatformSourceResolver(cache)
    private fun jar(path: String, vararg metadata: Pair<String, String>) {
        val file = root.resolve(path)
        file.parent.createDirectories()
        ZipOutputStream(file.outputStream()).use { zip ->
            metadata.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
        }
    }
    private fun pom(artifact: String, version: String = "1.2.3") = "META-INF/maven/org.example/$artifact/pom.properties" to "groupId=org.example\nartifactId=$artifact\nversion=$version"
    private fun input(owner: String? = null) = SourceDiscoveryInput(root.toString(), "IU", "2024.3", "243.123.4", listOf("lib/a.jar", "lib/b.jar"), listOf(
        SourceOwner("one", "duplicate", null, "plugin", listOf("alias"), listOf("two"), listOf("lib/a.jar")),
        SourceOwner("two", "duplicate", null, "module", emptyList(), emptyList(), listOf("lib/a.jar", "lib/b.jar")),
    ), owner)

    @Test fun `product sources follow version transitions and exceptions`() {
        fun artifact(product: String, build: String) = IntelliJPlatformSourceResolver.platformCandidates(product, "", build).first().artifact
        assertEquals("ideaIC", artifact("IU", "241.1"))
        assertEquals("ideaIU", artifact("IU", "242.1"))
        assertEquals("ideaIC", artifact("IC", "242.1"))
        assertEquals("idea", artifact("IU", "253.1"))
        assertEquals("idea", artifact("IC", "253.1"))
        assertEquals("pycharmPC", artifact("PY", "253.1"))
        assertEquals("pycharmPC", artifact("PC", "242.1"))
        assertEquals("ideaIC", artifact("RR", "242.1"))
        assertEquals(listOf("243.1", "[243,243.1]!!243.1", "243-SNAPSHOT"), IntelliJPlatformSourceResolver.platformCandidates("IU", "", "IU-243.1+7").map { it.version })
    }

    @Test fun `all embedded coordinates are retained with shared owners and local candidates`() {
        jar("lib/a.jar", pom("a"), pom("b"))
        jar("lib/b.jar", pom("a"))
        jar("src.zip", "license.txt" to "license")
        jar("lib/src/more.jar", "test/A.java" to "class A {}")
        val plan = resolver.discover(input())
        assertEquals(2, plan.binaries.size)
        assertEquals(listOf("exactCoordinate", "compatibleFallback", "snapshotFallback"),
            plan.sources.single { it.origin == "platformMapping" }.candidateProvenance)
        val a = plan.sources.single { it.origin == "embeddedMaven" && it.candidates.single().artifact == "a" }
        assertEquals(2, a.binaries.size)
        assertEquals(listOf("one", "two"), a.owners)
        assertEquals(2, a.evidence.size)
        assertEquals("notRequested", a.availability)
        assertEquals(2, plan.sources.count { it.origin == "local" && it.availability == "available" })
        assertEquals(plan, resolver.discover(input()))
    }

    @Test fun `owner filtering precedes inspection and unknown owners select nothing`() {
        jar("lib/a.jar", pom("a"))
        jar("lib/b.jar", pom("b"))
        val scoped = resolver.discover(input("alias"))
        assertEquals(listOf("lib/a.jar"), scoped.binaries.map { it.path })
        assertEquals(listOf("one", "two"), scoped.binaries.single().owners)
        assertEquals(1, cache.resolve("metadata/1").listDirectoryEntries("*.json").size)
        assertEquals(2, resolver.discover(input("duplicate")).binaries.size)
        val empty = resolver.discover(input("unknown"))
        assertTrue(empty.binaries.isEmpty())
        assertTrue(empty.sources.isEmpty())
    }

    @Test fun `malformed dynamic and oversized metadata retain usable results`() {
        jar("lib/a.jar", pom("good"), pom("placeholder", "\${version}"), pom("dynamic", "1.+"), pom("snapshot", "1-SNAPSHOT"),
            "META-INF/maven/x/bad/pom.properties" to "groupId=x", "META-INF/maven/x/large/pom.properties" to "x".repeat(70_000))
        jar("lib/b.jar")
        val plan = resolver.discover(input())
        assertEquals(listOf("good"), plan.sources.filter { it.origin == "embeddedMaven" }.map { it.candidates.single().artifact })
        assertEquals(listOf("metadata.invalid", "metadata.limit"), plan.binaries.first().diagnostics)
        assertTrue(plan.binaries.last().diagnostics.isEmpty())
    }

    @Test fun `cache reuses intrinsic observations regenerates invalid entries and notices changed content`() {
        jar("lib/a.jar", pom("a"))
        jar("lib/b.jar")
        val initial = resolver.discover(input())
        val metadata = cache.resolve("metadata/1/${initial.binaries.first().fingerprint}.json")
        val modified = metadata.getLastModifiedTime()
        val changedOwners = input().copy(owners = input().owners.map { it.copy(ref = "new-${it.ref}", dependencies = emptyList()) })
        assertEquals(listOf("new-one", "new-two"), resolver.discover(changedOwners).binaries.first().owners)
        assertEquals(modified, metadata.getLastModifiedTime())
        assertFalse(metadata.readText().contains("new-one"))
        metadata.writeText("invalid")
        assertEquals(initial, resolver.discover(input()))
        jar("lib/a.jar", pom("a", "2.0"))
        val changed = resolver.discover(input())
        assertNotEquals(initial.inventoryFingerprint, changed.inventoryFingerprint)
        assertEquals("2.0", changed.sources.single { it.origin == "embeddedMaven" }.candidates.single().version)
        metadata.deleteExisting()
        assertEquals(changed, resolver.discover(input()))
    }

    @Test fun `metadata count is bounded and corrupt binaries retain other results`() {
        jar("lib/a.jar", *(0..300).map { pom("library$it") }.toTypedArray())
        root.resolve("lib/b.jar").writeText("not an archive")
        val plan = resolver.discover(input())
        assertEquals(256, plan.sources.count { it.origin == "embeddedMaven" })
        assertEquals(listOf("metadata.limit"), plan.binaries.first().diagnostics)
        assertEquals(listOf("archive.unreadable"), plan.binaries.last().diagnostics)
        assertFalse(cache.resolve("metadata/1/${plan.binaries.last().fingerprint}.json").exists())
        assertEquals(plan, resolver.discover(input()))
    }
}