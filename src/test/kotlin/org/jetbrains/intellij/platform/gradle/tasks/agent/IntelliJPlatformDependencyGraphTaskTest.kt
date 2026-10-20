// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.json.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformTestBase
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.*

class IntelliJPlatformDependencyGraphTaskTest : IntelliJPlatformTestBase() {

    private fun fixture(extra: String = "") {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"dependency-graph-test\"")
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("org.jetbrains.intellij.platform") }
            repositories {
                maven {
                    url = uri("repo")
                    metadataSources { mavenPom() }
                }
            }
            val graphDeclarations = configurations.create("graphDeclarations") {
                isCanBeResolved = false
                isCanBeConsumed = false
            }
            val graphRuntime = configurations.create("graphRuntime") {
                isCanBeResolved = true
                isCanBeConsumed = false
                extendsFrom(graphDeclarations)
            }
            $extra
        """.trimIndent())
    }

    private fun module(name: String, version: String, dependencies: String = "") {
        // Deliberately publish metadata without a JAR: artifact resolution must not be necessary for these reports.
        dir.resolve("repo/example/$name/$version").createDirectories().resolve("$name-$version.pom").writeText("""
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>example</groupId><artifactId>$name</artifactId><version>$version</version>
                <dependencies>$dependencies</dependencies>
            </project>
        """.trimIndent())
    }

    private fun dependency(name: String, version: String) =
        "<dependency><groupId>example</groupId><artifactId>$name</artifactId><version>$version</version></dependency>"

    private fun conflictFixture(extra: String = "") {
        module("parent", "1.0", dependency("leaf", "1.0"))
        module("leaf", "1.0")
        module("leaf", "2.0")
        fixture("""
            dependencies {
                add("graphDeclarations", "example:parent:1.0")
                add("graphDeclarations", "example:leaf:2.0")
            }
            configurations.configureEach {
                if (name != "graphRuntime") {
                    incoming.beforeResolve { error("Unselected configuration resolved: " + name) }
                }
            }
            $extra
        """.trimIndent())
    }

    private fun report(path: String = "build/reports/intellij-platform/dependencygraph.json") =
        Json.parseToJsonElement(dir.resolve(path).readText()).jsonObject

    private fun JsonObject.configurations() = getValue("data").jsonObject.getValue("configurations").jsonArray.map { it.jsonObject }

    private fun JsonObject.resolution() = configurations().single().getValue("resolution").jsonObject

    @Test
    fun `declaration-only default never realizes deferred dependencies or resolves and reuses configuration cache`() {
        fixture("""
            dependencies {
                intellijPlatform { intellijIdeaUltimate("2025.1.6") }
                implementation("example:compile:1.0") { exclude(group = "excluded", module = "child") }
                testImplementation("example:test:1.0")
                runtimeOnly("example:runtime:1.0")
                constraints { implementation("example:constrained:1.0") }
            }
            configurations.named("graphDeclarations") {
                dependencies.addLater(providers.provider { error("Deferred declaration was realized") })
            }
            configurations.named("implementation") { exclude(group = "excluded", module = "all") }
            configurations.configureEach {
                incoming.beforeResolve { error("Unexpected dependency resolution: " + name) }
            }
        """.trimIndent())
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH, args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformDependencyGraph"), tasks.map { it.path })
        }
        val first = report()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH, args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformDependencyGraph"), tasks.map { it.path })
        }
        assertEquals(first, report())
        assertEquals("dependencyGraph", first.getValue("reportType").jsonPrimitive.content)
        assertEquals(":", first.getValue("projectPath").jsonPrimitive.content)
        assertEquals("declared", first.getValue("data").jsonObject.getValue("mode").jsonPrimitive.content)
        val configurations = first.configurations()
        assertEquals(configurations.map { it.getValue("name").jsonPrimitive.content }.sorted(), configurations.map { it.getValue("name").jsonPrimitive.content })
        assertTrue(configurations.all { it.getValue("resolution") == JsonNull })
        val implementation = configurations.single { it.getValue("name").jsonPrimitive.content == "implementation" }
        assertEquals("compile", implementation.getValue("roles").jsonArray.single().jsonPrimitive.content)
        val declared = implementation.getValue("dependencies").jsonArray.single().jsonObject
        assertEquals("compile", declared.getValue("name").jsonPrimitive.content)
        assertEquals("child", declared.getValue("exclusions").jsonArray.single().jsonObject.getValue("module").jsonPrimitive.content)
        assertEquals("all", implementation.getValue("exclusions").jsonArray.single().jsonObject.getValue("module").jsonPrimitive.content)
        assertEquals("constrained", implementation.getValue("constraints").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
        assertContains(configurations.single { it.getValue("name").jsonPrimitive.content == "testImplementation" }.getValue("roles").jsonArray.map { it.jsonPrimitive.content }, "test")
        assertContains(configurations.single { it.getValue("name").jsonPrimitive.content == "runtimeOnly" }.getValue("roles").jsonArray.map { it.jsonPrimitive.content }, "runtime")
        assertContains(configurations.single { it.getValue("name").jsonPrimitive.content == "intellijPlatformPluginModule" }.getValue("roles").jsonArray.map { it.jsonPrimitive.content }, "packaging")
        assertContains(configurations.single { it.getValue("name").jsonPrimitive.content == "intellijPlatformDependency" }.getValue("roles").jsonArray.map { it.jsonPrimitive.content }, "platform")
    }

    @Test
    fun `configuration and dependency filters apply to declarations without resolution`() {
        fixture("""
            dependencies {
                add("graphDeclarations", "example:alpha:1.0")
                add("graphDeclarations", "example:beta:2.0")
            }
            configurations.configureEach { incoming.beforeResolve { error("Unexpected resolution") } }
        """.trimIndent())
        val output = "build/custom reports/dependencies.json"
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH, "--configuration=graphDeclarations", "--dependency=example:beta", "--output=$output", args = listOf("--offline"))
        val first = report(output)
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH, "--configuration=graphDeclarations", "--dependency=example:beta", "--output=$output", args = listOf("--offline")) {
            assertConfigurationCacheReused()
        }
        assertEquals(first, report(output))
        val configuration = first.configurations().single()
        assertEquals("graphDeclarations", configuration.getValue("name").jsonPrimitive.content)
        assertEquals("beta", configuration.getValue("dependencies").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun `metadata resolution reports transitive conflicts reasons variants and reuses configuration cache`() {
        conflictFixture()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH, "--configuration=graphRuntime", "--resolve", args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformDependencyGraph"), tasks.map { it.path })
        }
        val first = report()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH, "--configuration=graphRuntime", "--resolve", args = listOf("--offline")) {
            assertConfigurationCacheReused()
        }
        assertEquals(first, report())
        val resolution = first.resolution()
        assertFalse(resolution.getValue("failed").jsonPrimitive.boolean)
        val declarations = first.configurations().single().getValue("dependencies").jsonArray.map { it.jsonObject }
        assertEquals(setOf("parent", "leaf"), declarations.map { it.getValue("name").jsonPrimitive.content }.toSet())
        assertTrue(declarations.all { it.getValue("declaredIn").jsonPrimitive.content == "graphDeclarations" })
        val components = resolution.getValue("components").jsonArray.map { it.jsonObject }
        val selected = components.single { it.getValue("id").jsonPrimitive.content == "module:example:leaf:2.0" }
        assertTrue(selected.getValue("selectionReasons").jsonArray.any { it.jsonObject.getValue("cause").jsonPrimitive.content == "CONFLICT_RESOLUTION" })
        assertTrue(selected.getValue("variants").jsonArray.isNotEmpty())
        val edge = resolution.getValue("edges").jsonArray.map { it.jsonObject }.single {
            it.getValue("from").jsonPrimitive.content == "module:example:parent:1.0"
        }
        assertEquals("1.0", edge.getValue("requested").jsonObject.getValue("version").jsonPrimitive.content)
        assertEquals("2.0", edge.getValue("selectedVersion").jsonPrimitive.content)
        assertEquals("module:example:leaf:2.0", edge.getValue("selected").jsonPrimitive.content)
        assertTrue(edge.getValue("variant").jsonObject.getValue("attributes").jsonObject.isNotEmpty())
        assertFalse(components.any { it.getValue("id").jsonPrimitive.content == "module:example:leaf:1.0" })
    }

    @Test
    fun `resolved filter matches selected versions and retains endpoints without changing conflict selection`() {
        conflictFixture()
        build(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH, "--configuration=graphRuntime", "--dependency=example:leaf:2.0", "--resolve", args = listOf("--offline"))
        val resolution = report().resolution()
        val edges = resolution.getValue("edges").jsonArray.map { it.jsonObject }
        assertEquals(2, edges.size)
        assertTrue(edges.all { it.getValue("selected").jsonPrimitive.content == "module:example:leaf:2.0" })
        val ids = resolution.getValue("components").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet()
        assertContains(ids, resolution.getValue("root").jsonPrimitive.content)
        edges.forEach {
            assertContains(ids, it.getValue("from").jsonPrimitive.content)
            assertContains(ids, it.getValue("selected").jsonPrimitive.content)
        }
    }

    @Test
    fun `unresolved dependency writes failed report before task failure even outside dependency filter`() {
        fixture("""
            dependencies { add("graphDeclarations", "example:missing:1.0") }
        """.trimIndent())
        buildAndFailWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH, "--configuration=graphRuntime", "--resolve", args = listOf("--offline"))
        val first = report()
        assertEquals("failed", first.getValue("status").jsonPrimitive.content)
        val unresolved = first.resolution().getValue("edges").jsonArray.single().jsonObject
        assertTrue(unresolved.getValue("unresolved").jsonPrimitive.boolean)
        assertEquals("missing", unresolved.getValue("requested").jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals(JsonNull, unresolved.getValue("selected"))
        buildAndFail(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH, "--configuration=graphRuntime", "--dependency=not-matching", "--resolve", args = listOf("--offline"))
        assertEquals("failed", report().getValue("status").jsonPrimitive.content)
        assertTrue(report().resolution().getValue("edges").jsonArray.isEmpty())
    }

    @Test
    fun `resolution honors declared dependency and configuration exclusions`() {
        module("parent", "1.0", dependency("excluded-child", "1.0") + dependency("excluded-globally", "1.0"))
        fixture("""
            dependencies {
                add("graphDeclarations", "example:parent:1.0") {
                    exclude(group = "example", module = "excluded-child")
                }
            }
            configurations.named("graphRuntime") { exclude(group = "example", module = "excluded-globally") }
        """.trimIndent())
        build(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH, "--configuration=graphRuntime", "--resolve", args = listOf("--offline"))
        val graph = report()
        assertFalse(graph.resolution().getValue("failed").jsonPrimitive.boolean)
        assertEquals(1, graph.resolution().getValue("edges").jsonArray.size)
        val configuration = graph.configurations().single()
        assertEquals("excluded-globally", configuration.getValue("exclusions").jsonArray.single().jsonObject.getValue("module").jsonPrimitive.content)
        assertEquals("excluded-child", configuration.getValue("dependencies").jsonArray.single().jsonObject.getValue("exclusions").jsonArray.single().jsonObject.getValue("module").jsonPrimitive.content)
    }

    @Test
    fun `resolver exception details are not serialized`() {
        fixture("""
            configurations.named("graphRuntime") {
                incoming.beforeResolve { error("https://username:secret-do-not-report@example.invalid/private") }
            }
        """.trimIndent())
        buildAndFail(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH, "--configuration=graphRuntime", "--resolve", args = listOf("--offline"))
        val report = report()
        assertEquals("failed", report.getValue("status").jsonPrimitive.content)
        assertFalse(report.toString().contains("secret-do-not-report"))
        assertFalse(report.toString().contains("example.invalid"))
        assertEquals("resolution.failed", report.getValue("issues").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun `unknown and non-resolvable configuration selections write actionable failed reports`() {
        fixture()
        buildAndFail(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH, "--configuration=doesNotExist", args = listOf("--offline"))
        assertEquals("failed", report().getValue("status").jsonPrimitive.content)
        assertEquals("configuration.notFound", report().getValue("issues").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
        buildAndFail(Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH, "--configuration=graphDeclarations", "--resolve", args = listOf("--offline"))
        assertEquals("failed", report().getValue("status").jsonPrimitive.content)
        assertEquals("configuration.notResolvable", report().getValue("issues").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
    }
}