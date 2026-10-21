// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.*
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.models.SourceDiscoveryInput
import org.jetbrains.intellij.platform.gradle.providers.SourceArtifactOutcome
import org.jetbrains.intellij.platform.gradle.resolvers.sources.IntelliJPlatformSourceResolver
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*
import kotlin.test.*

class IntelliJPlatformSourcesTaskTest : IntelliJPluginTestBase() {
    override val enableIntelliJPlatformCache = true
    private fun report(path: String = "build/reports/intellij-platform/sources.json") = Json.parseToJsonElement(dir.resolve(path).readText()).jsonObject
    private fun data() = report().getValue("data").jsonObject

    private fun assertComplete(report: JsonObject = report()) {
        assertEquals("complete", report.getValue("status").jsonPrimitive.content,
            report.getValue("data").jsonObject.getValue("binaries").jsonArray.filter {
                it.jsonObject.getValue("inspection").jsonPrimitive.content != "inspected"
            }.toString())
        assertTrue(report.getValue("issues").jsonArray.isEmpty())
        val sources = report.getValue("data").jsonObject.getValue("sources").jsonArray
        val candidates = sources.filter { it.jsonObject.getValue("availability").jsonPrimitive.content == "notRequested" }
            .map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet()
        val unavailable = report.getValue("unavailable").jsonArray
        assertEquals(candidates, unavailable.map { it.jsonObject.getValue("observation").jsonPrimitive.content }.toSet())
        assertTrue(unavailable.all { it.jsonObject.getValue("state").jsonPrimitive.content == "notRequested" })
    }

    private fun Path.archive(vararg entries: Pair<String, String>) {
        parent.createDirectories()
        ZipOutputStream(outputStream()).use { zip ->
            entries.forEach { (path, text) -> zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry() }
        }
    }

    @Test
    fun `local SDK changes at the same path remain fresh with configuration cache`() {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"local-sdk\"")
        val sdk = dir.resolve("local SDK").createDirectories()
        sdk.resolve("build.txt").writeText("IC-243.28141.18")
        sdk.resolve("product-info.json").writeText("""
            {
                "name": "IntelliJ IDEA Community Edition", "version": "2024.3", "buildNumber": "243.28141.18", "productCode": "IC",
                "dataDirectoryName": "IdeaIC2024.3", "svgIconPath": "bin/idea.svg", "productVendor": "JetBrains", "modules": [], "bundledPlugins": [],
                "launch": [
                    {"os": "macOS", "bootClassPathJarNames": ["platform.jar"]},
                    {"os": "Linux", "bootClassPathJarNames": ["platform.jar"]},
                    {"os": "Windows", "bootClassPathJarNames": ["platform.jar"]}
                ],
                "layout": [{"name": "com.intellij", "kind": "plugin", "classPath": ["lib/platform.jar"]}]
            }
        """.trimIndent())
        val plugin = "META-INF/plugin.xml" to "<idea-plugin><id>com.intellij</id><name>Platform</name><version>1.0</version><vendor>JetBrains</vendor></idea-plugin>"
        fun writeBinary(version: String) = sdk.resolve("lib/platform.jar").archive(plugin,
            "META-INF/maven/org.example/library/pom.properties" to "groupId=org.example\nartifactId=library\nversion=$version")
        writeBinary("1.0")
        sdk.resolve("modules/module-descriptors.jar").archive()
        sdk.resolve("src.zip").archive("org/example/Library.java" to "class Library {}", "org/example/library.java" to "class library {}")
        dir.resolve("external.jar").archive("META-INF/maven/org.other/external/pom.properties" to "groupId=org.other\nartifactId=external\nversion=1.0")
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("org.jetbrains.intellij.platform.module") }
            repositories { intellijPlatform { defaultRepositories() } }
            dependencies { intellijPlatform { local("${sdk.invariantSeparatorsPathString}") }; implementation(files("external.jar")) }
        """.trimIndent())
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--offline"))
        assertComplete()
        fun embeddedVersion() = data().getValue("sources").jsonArray.single { it.jsonObject.getValue("origin").jsonPrimitive.content == "embeddedMaven" }
            .jsonObject.getValue("candidates").jsonArray.single().jsonObject.getValue("version").jsonPrimitive.content
        assertEquals("1.0", embeddedVersion())
        assertTrue(data().getValue("sources").jsonArray.any { it.jsonObject.getValue("origin").jsonPrimitive.content == "local" })
        val fingerprint = data().getValue("inventoryFingerprint")
        writeBinary("2.0")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--offline"))
        assertEquals("2.0", embeddedVersion())
        assertComplete()
        assertNotEquals(fingerprint, data().getValue("inventoryFingerprint"))
        assertFalse(report().toString().contains("org.other"))

        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--extract", "--offline"))
        fun localSource() = data().getValue("sources").jsonArray.map { it.jsonObject }.single {
            it.getValue("origin").jsonPrimitive.content == "local"
        }
        val localRoot = Path(localSource().getValue("extractedRoot").jsonPrimitive.content)
        val localRoots = localSource().getValue("extractedRoots").jsonArray.map { Path(it.jsonPrimitive.content) }
        assertEquals(2, localRoots.size)
        assertEquals(localRoot, localRoots.first())
        assertEquals("class library {}", localRoots[1].resolve("org/example/library.java").readText())
        assertTrue(localSource().getValue("diagnostics").jsonArray.any { it.jsonPrimitive.content == "sources.extraction.caseCollisionRoots:2" })
        assertTrue(localRoot.startsWith(intellijPlatformCacheDir.resolve("sources").toRealPath()))
        assertEquals("class Library {}", localRoot.resolve("org/example/Library.java").readText())
        val extractedTime = localRoot.resolve("org/example/Library.java").getLastModifiedTime()
        build("clean")
        assertTrue(localRoot.exists())
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--extract", "--offline"))
        assertEquals(extractedTime, localRoot.resolve("org/example/Library.java").getLastModifiedTime())
        assertEquals(localRoot, Path(localSource().getValue("extractedRoot").jsonPrimitive.content))
        sdk.resolve("src.zip").archive("changed/Other.kt" to "class Other")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--extract", "--offline"))
        assertNotEquals(localRoot, Path(localSource().getValue("extractedRoot").jsonPrimitive.content))
        sdk.resolve("src.zip").archive("../unsafe" to "bad")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--extract", "--offline"))
        assertEquals("failed", localSource().getValue("extraction").jsonPrimitive.content)
        assertEquals(JsonNull, localSource().getValue("extractedRoot"))
        assertEquals("available", localSource().getValue("availability").jsonPrimitive.content)
        assertEquals("partial", report().getValue("status").jsonPrimitive.content)
        sdk.resolve("src.zip").archive("org/example/Library.java" to "class Library {}")

        val repository = dir.resolve("source repository").createDirectories()
        fun publish(version: String) {
            val module = repository.resolve("org/example/library/$version").createDirectories()
            module.resolve("library-$version.pom").writeText("""
                <project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId><artifactId>library</artifactId><version>$version</version></project>
            """.trimIndent())
            module.resolve("library-$version-sources.jar").archive("org/example/Library.java" to "class Library {}")
        }
        publish("2.0")
        dir.resolve("build.gradle.kts").appendText("""

            repositories.clear()
            repositories { intellijPlatform { localPlatformArtifacts() } }
            repositories { maven { url = uri("${repository.invariantSeparatorsPathString}") } }
        """.trimIndent())
        fun embedded() = data().getValue("sources").jsonArray.map { it.jsonObject }.filter {
            it.getValue("origin").jsonPrimitive.content == "embeddedMaven"
        }
        repeat(2) {
            buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download"))
            assertEquals("available", embedded().single().getValue("availability").jsonPrimitive.content)
            assertTrue(Path(embedded().single().getValue("archive").jsonPrimitive.content).isRegularFile())
            assertEquals("2.0", embedded().single().getValue("selectedCoordinate").jsonObject.getValue("version").jsonPrimitive.content)
            assertEquals("partial", report().getValue("status").jsonPrimitive.content)
        }
        sdk.resolve("lib/platform.jar").archive(plugin,
            "META-INF/maven/org.example/library/pom.properties" to "groupId=org.example\nartifactId=library\nversion=1.0",
            "META-INF/maven/another/library/pom.properties" to "groupId=org.example\nartifactId=library\nversion=2.0")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download", "--offline"))
        assertEquals(setOf("missingOffline", "available"),
            embedded().map { it.getValue("availability").jsonPrimitive.content }.toSet())
        publish("1.0")
        repository.resolve("org/example/library/2.0/library-2.0-sources.jar")
            .copyTo(repository.resolve("org/example/library/1.0/library-1.0-sources.jar"), overwrite = true)
        repeat(2) {
            buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download", "--offline", "--extract"))
            assertEquals(2, embedded().size)
            assertTrue(embedded().all { it.getValue("availability").jsonPrimitive.content == "available" })
            embedded().forEach {
                assertEquals("extracted", it.getValue("extraction").jsonPrimitive.content)
                assertTrue(Path(it.getValue("extractedRoot").jsonPrimitive.content).resolve("org/example/Library.java").isRegularFile())
            }
            assertEquals(1, embedded().map { it.getValue("extractedRoot") }.distinct().size)
            assertEquals(setOf("1.0", "2.0"), embedded().map {
                it.getValue("selectedCoordinate").jsonObject.getValue("version").jsonPrimitive.content
            }.toSet())
        }

        val platformModule = repository.resolve("com/jetbrains/intellij/idea/ideaIC").createDirectories()
        fun publishPlatform(version: String) {
            val module = platformModule.resolve(version).createDirectories()
            module.resolve("ideaIC-$version.pom").writeText("""
                <project><modelVersion>4.0.0</modelVersion><groupId>com.jetbrains.intellij.idea</groupId><artifactId>ideaIC</artifactId><version>$version</version></project>
            """.trimIndent())
            module.resolve("ideaIC-$version-sources.jar").archive("com/intellij/Platform.java" to "class Platform {}")
        }
        publishPlatform("243-SNAPSHOT")
        fun platform() = data().getValue("sources").jsonArray.map { it.jsonObject }.single {
            it.getValue("origin").jsonPrimitive.content == "platformMapping"
        }
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download", "--refresh-dependencies"))
        assertEquals("available", platform().getValue("availability").jsonPrimitive.content)
        assertEquals("snapshotFallback", platform().getValue("provenance").jsonPrimitive.content)
        assertEquals("243-SNAPSHOT", platform().getValue("selectedCoordinate").jsonObject.getValue("version").jsonPrimitive.content)
        publishPlatform("243.100")
        platformModule.resolve("maven-metadata.xml").writeText("""
            <metadata><groupId>com.jetbrains.intellij.idea</groupId><artifactId>ideaIC</artifactId><versioning><versions><version>243.100</version></versions></versioning></metadata>
        """.trimIndent())
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download", "--refresh-dependencies"))
        assertEquals("available", platform().getValue("availability").jsonPrimitive.content)
        assertEquals("compatibleFallback", platform().getValue("provenance").jsonPrimitive.content)
        assertEquals("243.100", platform().getValue("selectedCoordinate").jsonObject.getValue("version").jsonPrimitive.content)
        publishPlatform("243.28141.18")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download", "--refresh-dependencies"))
        assertEquals("exactCoordinate", platform().getValue("provenance").jsonPrimitive.content)
        assertEquals("complete", report().getValue("status").jsonPrimitive.content)

        val missing = repository.resolve("org/example/library/3.0").createDirectories()
        missing.resolve("library-3.0.pom").writeText("""
            <project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId><artifactId>library</artifactId><version>3.0</version></project>
        """.trimIndent())
        writeBinary("3.0")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download"))
        assertEquals("notFound", embedded().single().getValue("availability").jsonPrimitive.content)
        assertEquals("available", platform().getValue("availability").jsonPrimitive.content)
        assertEquals("partial", report().getValue("status").jsonPrimitive.content)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val rejectRequests = AtomicBoolean(true)
        val requests = AtomicInteger()
        server.createContext("/private-repository") { exchange ->
            requests.incrementAndGet()
            try {
                val file = repository.resolve(exchange.requestURI.path.removePrefix("/private-repository/")).normalize()
                when {
                    rejectRequests.get() -> exchange.sendResponseHeaders(401, -1)
                    !file.startsWith(repository) || !file.isRegularFile() -> exchange.sendResponseHeaders(404, -1)
                    exchange.requestMethod == "HEAD" -> exchange.sendResponseHeaders(200, -1)
                    else -> {
                        val bytes = file.readBytes()
                        exchange.sendResponseHeaders(200, bytes.size.toLong())
                        exchange.responseBody.write(bytes)
                    }
                }
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            val script = dir.resolve("build.gradle.kts")
            script.writeText(script.readText().replace(
                "url = uri(\"${repository.invariantSeparatorsPathString}\")",
                """url = uri("http://127.0.0.1:${server.address.port}/private-repository"); isAllowInsecureProtocol = true; credentials { username = "private-user"; password = "private-password" }"""))
            val beforeDiscovery = requests.get()
            buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES)
            assertEquals(beforeDiscovery, requests.get())
            val undispatched = data().getValue("sources").jsonArray.map { it.jsonObject }
                .filter { it.getValue("origin").jsonPrimitive.content != "local" }
            assertTrue(undispatched.isNotEmpty())
            undispatched.forEach {
                assertEquals("notRequested", it.getValue("availability").jsonPrimitive.content)
                assertEquals(JsonNull, it.getValue("archive"))
            }
            buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download"))
            assertTrue(data().getValue("sources").jsonArray.map { it.jsonObject }
                .filter { it.getValue("origin").jsonPrimitive.content != "local" }
                .all { it.getValue("availability").jsonPrimitive.content == "resolutionError" })
            val output = report().toString()
            listOf("private-repository", "private-user", "private-password", "127.0.0.1").forEach { assertFalse(output.contains(it)) }
            rejectRequests.set(false)
            publish("3.0")
            buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download"))
            assertEquals("available", embedded().single().getValue("availability").jsonPrimitive.content)
            assertFalse(Path(embedded().single().getValue("archive").jsonPrimitive.content).startsWith(repository))
            val resolvedReport = report()
            val reused = buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download"))
            assertContains(reused.output, "Reusing configuration cache.")
            assertTrue(reused.tasks.all { it.path in listOf(":initializeIntellijPlatformPlugin", ":intellijPlatformSources") })
            assertEquals(resolvedReport, report())
            val cachedArchive = Path(embedded().single().getValue("archive").jsonPrimitive.content)
            cachedArchive.deleteExisting()
            val beforeRedownload = requests.get()
            val redownload = buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download"))
            assertFalse(redownload.output.contains("Reusing configuration cache."))
            assertEquals("available", embedded().single().getValue("availability").jsonPrimitive.content)
            assertTrue(cachedArchive.isRegularFile())
            assertTrue(requests.get() > beforeRedownload)
            val requestCount = requests.get()
            rejectRequests.set(true)
            buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download", "--offline"))
            assertEquals("available", embedded().single().getValue("availability").jsonPrimitive.content)
            assertEquals(requestCount, requests.get())
            writeBinary("4.0")
            buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download", "--offline"))
            assertEquals("missingOffline", embedded().single().getValue("availability").jsonPrimitive.content)
            val offlineSuggestion = report().getValue("unavailable").jsonArray.map { it.jsonObject }
                .first { it.getValue("state").jsonPrimitive.content == "missingOffline" }.getValue("reason").jsonPrimitive.content
            assertContains(offlineSuggestion, "without --offline")
            assertContains(offlineSuggestion, "--download")
            assertEquals(requestCount, requests.get())
            rejectRequests.set(false)
            publish("4.0")
            buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download"))
            assertEquals("available", embedded().single().getValue("availability").jsonPrimitive.content)
        } finally {
            server.stop(0)
        }
        val projectScript = dir.resolve("build.gradle.kts")
        val originalScript = projectScript.readText()
        projectScript.writeText("""
            plugins { id("org.jetbrains.intellij.platform.module") }
            dependencies { intellijPlatform { local("${sdk.invariantSeparatorsPathString}") } }
        """.trimIndent())
        dir.resolve("settings.gradle.kts").writeText("""
            import org.jetbrains.intellij.platform.gradle.extensions.*

            plugins { id("org.jetbrains.intellij.platform.settings") }
            rootProject.name = "local-sdk"
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    intellijPlatform { localPlatformArtifacts() }
                    maven { url = uri("${repository.invariantSeparatorsPathString}") }
                }
            }
        """.trimIndent())
        writeBinary("5.0")
        val settingsModule = repository.resolve("org/example/library/5.0").createDirectories()
        settingsModule.resolve("library-5.0.pom").writeText("""
            <project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId><artifactId>library</artifactId><version>5.0</version></project>
        """.trimIndent())
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download"))
        assertEquals("notFound", embedded().single().getValue("availability").jsonPrimitive.content)
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"local-sdk\"")
        projectScript.writeText(originalScript)
        dir.resolve("build.gradle.kts").appendText("""

            listOf("intellijPlatformDependency", "intellijPlatformClasspath", "intellijPlatformBundledPlugins", "intellijPlatformBundledModules")
                .forEach { configurations.getByName(it).resolve() }
            repositories.clear()
        """.trimIndent())
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download"))
        assertEquals("resolutionError", embedded().single().getValue("availability").jsonPrimitive.content)
        val repositoryReason = report().getValue("unavailable").jsonArray.map { it.jsonObject }
            .first { it.getValue("state").jsonPrimitive.content == "resolutionError" }.getValue("reason").jsonPrimitive.content
        assertContains(repositoryReason, "configured repositories")
        assertContains(repositoryReason, "no repositories are added automatically")
        assertFalse(report().toString().contains("private-password"))
    }

    @Test
    fun `subprojects share default extraction cache and regenerate deleted roots`() {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"shared-sources\"; include(\"a\", \"b\")")
        dir.resolve("gradle.properties").writeText(dir.resolve("gradle.properties").readLines()
            .filterNot { it.startsWith("org.jetbrains.intellij.platform.intellijPlatformCache=") }.joinToString("\n"))
        dir.resolve("build.gradle.kts").writeText("plugins { id(\"org.jetbrains.intellij.platform.module\") apply false }")
        val sdk = dir.resolve("sdk").createDirectories()
        sdk.resolve("build.txt").writeText("IC-243.1")
        sdk.resolve("product-info.json").writeText("""
            {"name":"IDEA","version":"2024.3","buildNumber":"243.1","productCode":"IC",
             "dataDirectoryName":"Idea","svgIconPath":"bin/idea.svg","productVendor":"JetBrains","modules":[],"bundledPlugins":[],
             "launch":[{"os":"macOS","bootClassPathJarNames":["platform.jar"]},{"os":"Linux","bootClassPathJarNames":["platform.jar"]},{"os":"Windows","bootClassPathJarNames":["platform.jar"]}],
             "layout":[{"name":"com.intellij","kind":"plugin","classPath":["lib/platform.jar"]}]}
        """.trimIndent())
        sdk.resolve("lib/platform.jar").archive("META-INF/plugin.xml" to "<idea-plugin><id>com.intellij</id><name>Platform</name><version>1</version><vendor>JetBrains</vendor></idea-plugin>")
        sdk.resolve("modules/module-descriptors.jar").archive()
        sdk.resolve("src.zip").archive("main/A.java" to "class A {}", "other/B.kt" to "class B", "LICENSE" to "license")
        listOf("a", "b").forEach { name ->
            dir.resolve(name).createDirectories().resolve("build.gradle.kts").writeText("""
                plugins { id("org.jetbrains.intellij.platform.module") }
                repositories { intellijPlatform { localPlatformArtifacts() } }
                dependencies { intellijPlatform { local("${sdk.invariantSeparatorsPathString}") } }
            """.trimIndent())
        }
        fun run(cache: Path? = null) = buildWithConfigurationCache(":a:intellijPlatformSources", "--extract", ":b:intellijPlatformSources", "--extract",
            args = listOf("--offline", "--parallel") + listOfNotNull(cache?.let { "-Porg.jetbrains.intellij.platform.intellijPlatformCache=${it.invariantSeparatorsPathString}" }))
        fun local(project: String) = report("$project/build/reports/intellij-platform/sources.json").getValue("data").jsonObject
            .getValue("sources").jsonArray.map { it.jsonObject }.single { it.getValue("origin").jsonPrimitive.content == "local" }
        run()
        val root = Path(local("a").getValue("extractedRoot").jsonPrimitive.content)
        assertEquals(local("a").getValue("extractedRoot"), local("b").getValue("extractedRoot"))
        assertTrue(root.startsWith(dir.resolve(".intellijPlatform/sources").toRealPath()))
        root.parent.toFile().deleteRecursively()
        run()
        assertEquals("class A {}", root.resolve("main/A.java").readText())
        val override = dir.resolve("custom cache with spaces")
        run(override)
        val overridden = Path(local("a").getValue("extractedRoot").jsonPrimitive.content)
        assertTrue(overridden.startsWith(override.toRealPath()))
        assertEquals(local("a").getValue("extractedRoot"), local("b").getValue("extractedRoot"))
    }

    @Test
    fun `discovery is deterministic and scoped`() {
        dir.resolve("build.gradle.kts").appendText("""

            dependencies { intellijPlatform { bundledPlugin("com.intellij.java") } }
        """.trimIndent())
        val result = build(Tasks.INTELLIJ_PLATFORM_SOURCES)
        assertTrue(result.tasks.all { it.path in listOf(":initializeIntellijPlatformPlugin", ":intellijPlatformSources") })
        assertEquals("sources", report().getValue("reportType").jsonPrimitive.content)
        assertEquals("partial", report().getValue("status").jsonPrimitive.content)
        val partialBinary = data().getValue("binaries").jsonArray.map { it.jsonObject }
            .single { it.getValue("inspection").jsonPrimitive.content == "partial" }
        assertEquals("lib/lib-client.jar", partialBinary.getValue("path").jsonPrimitive.content)
        assertEquals(listOf("metadata.invalid"), partialBinary.getValue("diagnostics").jsonArray.map { it.jsonPrimitive.content })
        val unavailable = report().getValue("unavailable").jsonArray.map { it.jsonObject }
        assertEquals(partialBinary.getValue("id"), unavailable.single { it.getValue("state").jsonPrimitive.content == "unavailable" }.getValue("observation"))
        assertEquals(data().getValue("sources").jsonArray.filter { it.jsonObject.getValue("availability").jsonPrimitive.content == "notRequested" }
            .map { it.jsonObject.getValue("id") }.toSet(),
            unavailable.filter { it.getValue("state").jsonPrimitive.content == "notRequested" }.map { it.getValue("observation") }.toSet())
        assertTrue(intellijPlatformCacheDir.resolve("sources/metadata/1").isDirectory())
        assertTrue(data().getValue("binaries").jsonArray.isNotEmpty())
        val ownerRefs = data().getValue("owners").jsonArray.map { it.jsonObject.getValue("ref").jsonPrimitive.content }.toSet()
        data().getValue("binaries").jsonArray.forEach { binary ->
            assertTrue(binary.jsonObject.getValue("owners").jsonArray.all { it.jsonPrimitive.content in ownerRefs })
            assertFalse(Path(binary.jsonObject.getValue("path").jsonPrimitive.content).isAbsolute)
        }
        val initial = report()
        build(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--offline"))
        assertEquals(initial, report())
        build(Tasks.INTELLIJ_PLATFORM_SOURCES, "--owner=com.intellij.java", args = listOf("--offline"))
        assertTrue(data().getValue("binaries").jsonArray.isNotEmpty())
        assertTrue(data().getValue("binaries").jsonArray.size < initial.getValue("data").jsonObject.getValue("binaries").jsonArray.size)
        build(Tasks.INTELLIJ_PLATFORM_SOURCES, "--owner=unknown-owner", "--output=build/custom reports/sources.json", args = listOf("--offline"))
        val emptyReport = report("build/custom reports/sources.json")
        assertComplete(emptyReport)
        val empty = emptyReport.getValue("data").jsonObject
        assertTrue(empty.getValue("binaries").jsonArray.isEmpty())
        assertTrue(empty.getValue("sources").jsonArray.isEmpty())
    }

    @Test
    fun `module configuration cache and source cache override survive clean`() {
        build(Tasks.INTELLIJ_PLATFORM_SOURCES)
        val root = data().getValue("root").jsonPrimitive.content
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("org.jetbrains.intellij.platform.module") }
            repositories { intellijPlatform { defaultRepositories() } }
            dependencies { intellijPlatform { local("$root"); bundledPlugin("com.intellij.java") } }
        """.trimIndent())
        val cache = dir.resolve("custom cache")
        val args = listOf("--offline", "-Porg.jetbrains.intellij.platform.intellijPlatformCache=${cache.invariantSeparatorsPathString}")
        build(Tasks.INTELLIJ_PLATFORM_SOURCES, "--owner=com.intellij.java", args = args)
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, "--owner=com.intellij.java", args = args)
        val initial = report()
        assertComplete(initial)
        val metadata = cache.resolve("sources/metadata/1").listDirectoryEntries("*.json")
        assertTrue(metadata.isNotEmpty())
        val times = metadata.associateWith { it.getLastModifiedTime() }
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SOURCES, "--owner=com.intellij.java", args = args) { assertConfigurationCacheReused() }
        assertEquals(initial, report())
        build("clean", Tasks.INTELLIJ_PLATFORM_SOURCES, "--owner=com.intellij.java", args = args)
        assertEquals(initial, report())
        times.forEach { (path, time) -> assertEquals(time, path.getLastModifiedTime()) }
    }

    @Test
    fun `missing and partially inspected binaries retain candidates in a partial report`() {
        dir.resolve("lib/malformed.jar").archive("META-INF/maven/org.example/library/pom.properties" to "groupId=org.example")
        dir.resolve("lib/good.jar").archive("META-INF/maven/org.example/library/pom.properties" to
            "groupId=org.example\nartifactId=library\nversion=1.0")
        val plan = IntelliJPlatformSourceResolver(dir.resolve("sources-cache")).discover(
            SourceDiscoveryInput(dir.invariantSeparatorsPathString, "IC", "2024.3", "243.1",
                listOf("lib/missing.jar", "lib/malformed.jar", "lib/good.jar"), emptyList())
        )
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        val task = project.tasks.register("sourcesReport", IntelliJPlatformSourcesTask::class.java).get()
        task.projectPath.set(":")
        task.pluginVersion.set("test")
        task.outputFile.set(dir.resolve("build/reports/intellij-platform/sources.json").toFile())
        task.discoveryResult.set(Json.encodeToString(plan))
        task.report()

        assertEquals("partial", report().getValue("status").jsonPrimitive.content)
        val binaries = data().getValue("binaries").jsonArray.map { it.jsonObject }
        val missing = binaries.single { it.getValue("path").jsonPrimitive.content == "lib/missing.jar" }
        assertFalse(missing.getValue("exists").jsonPrimitive.boolean)
        assertEquals(listOf("binary.missing"), missing.getValue("diagnostics").jsonArray.map { it.jsonPrimitive.content })
        val malformed = binaries.single { it.getValue("path").jsonPrimitive.content == "lib/malformed.jar" }
        assertEquals(listOf("metadata.invalid"), malformed.getValue("diagnostics").jsonArray.map { it.jsonPrimitive.content })
        val unavailable = report().getValue("unavailable").jsonArray.map { it.jsonObject }
        assertEquals(setOf(missing.getValue("id"), malformed.getValue("id")),
            unavailable.filter { it.getValue("state").jsonPrimitive.content == "unavailable" }.map { it.getValue("observation") }.toSet())
        assertEquals(plan.sources.map { it.id }.toSet(), unavailable.filter { it.getValue("state").jsonPrimitive.content == "notRequested" }
            .map { it.getValue("observation").jsonPrimitive.content }.toSet())
        assertTrue(data().getValue("sources").jsonArray.any { it.jsonObject.getValue("origin").jsonPrimitive.content == "embeddedMaven" })
        val coordinate = plan.sources.single { it.origin == "embeddedMaven" }.candidates.single()
        task.download.set(true)
        task.artifactOutcomes.set(listOf(Json.encodeToString(SourceArtifactOutcome(
            coordinate, dir.resolve("deleted-sources.jar").toString(), "available", coordinate))))
        task.report()
        val missingArchive = data().getValue("sources").jsonArray.map { it.jsonObject }
            .single { it.getValue("origin").jsonPrimitive.content == "embeddedMaven" }
        assertEquals("resolutionError", missingArchive.getValue("availability").jsonPrimitive.content)
        assertEquals(JsonNull, missingArchive.getValue("archive"))
    }

    @Test
    fun `missing platform dependency writes a sanitized failed report`() {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"missing-platform\"")
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("org.jetbrains.intellij.platform.module") }
        """.trimIndent())
        buildAndFail(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--offline", "--no-configuration-cache")) {
            assertTrue(output.contains("IntelliJ Platform source discovery failed; see the JSON report."))
        }
        assertEquals("failed", report().getValue("status").jsonPrimitive.content)
        assertEquals(JsonNull, report().getValue("data"))
        assertTrue(report().getValue("unavailable").jsonArray.isEmpty())
        val issue = report().getValue("issues").jsonArray.single().jsonObject
        assertEquals("sources.discovery.failed", issue.getValue("id").jsonPrimitive.content)
        assertEquals("error", issue.getValue("severity").jsonPrimitive.content)
        assertEquals("Could not inventory the selected IntelliJ Platform sources.", issue.getValue("message").jsonPrimitive.content)
        assertFalse(report().toString().contains("No IntelliJ Platform dependency"))
        assertFalse(report().toString().contains("https://"))
        buildAndFail(Tasks.INTELLIJ_PLATFORM_SOURCES, args = listOf("--download", "--offline", "--no-configuration-cache")) {
            assertContains(output, "IntelliJ Platform source discovery failed; see the JSON report.")
        }
        assertEquals("failed", report().getValue("status").jsonPrimitive.content)
        assertEquals(JsonNull, report().getValue("data"))
        assertEquals(issue, report().getValue("issues").jsonArray.single().jsonObject)
        assertFalse(report().toString().contains("No IntelliJ Platform dependency"))
    }
}
