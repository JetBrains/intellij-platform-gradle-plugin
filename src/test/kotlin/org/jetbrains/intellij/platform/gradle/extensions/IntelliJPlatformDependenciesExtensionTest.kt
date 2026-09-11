// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Ignore
import kotlin.test.Test

class IntelliJPlatformDependenciesExtensionTest : IntelliJPluginTestBase() {

    @Test
    fun `bundled plugin sources resolve separately from classpath artifacts`() {
        val platformPath = dir.resolve("fake-platform")
        val ivyPath = dir.resolve("fake-ivy")
        writeBundledPluginIvyFixture(platformPath, ivyPath)

        buildFile write //language=kotlin
                """
                repositories {
                    ivy {
                        name = "Bundled Plugin Sources Test"
                        ivyPattern("${ivyPath.invariantSeparatorsPathString}/[revision]/[organization]-[module]-[revision].[ext]")
                        artifactPattern("/[artifact]")
                        content {
                            includeModule("bundledPlugin", "plugin-a")
                        }
                    }
                }

                val bundledPluginUnderTest by configurations.creating

                dependencies {
                    add(bundledPluginUnderTest.name, "bundledPlugin:plugin-a:IC-241.1")
                    components.all<org.jetbrains.intellij.platform.gradle.artifacts.LocalIvyArtifactPathComponentMetadataRule> {
                        params(
                            "${platformPath.invariantSeparatorsPathString}",
                            "${ivyPath.invariantSeparatorsPathString}",
                        )
                    }
                }

                tasks.register("verifyBundledPluginSources") {
                    doLast {
                        val component = bundledPluginUnderTest.incoming.resolutionResult.allComponents
                            .map { it.id }
                            .filterIsInstance<org.gradle.api.artifacts.component.ModuleComponentIdentifier>()
                            .single { it.group == "bundledPlugin" }
                        val sourceFiles = dependencies.createArtifactResolutionQuery()
                            .forComponents(component)
                            .withArtifacts(
                                org.gradle.jvm.JvmLibrary::class.java,
                                org.gradle.language.base.artifact.SourcesArtifact::class.java,
                            )
                            .execute()
                            .resolvedComponents
                            .flatMap { result ->
                                result.getArtifacts(org.gradle.language.base.artifact.SourcesArtifact::class.java)
                                    .filterIsInstance<org.gradle.api.artifacts.result.ResolvedArtifactResult>()
                                    .map { it.file.name }
                            }

                        check(bundledPluginUnderTest.files.map { it.name } == listOf("plugin-a.jar"))
                        check(sourceFiles == listOf("plugin-a-api-sources.jar")) {
                            "Expected only the bundled plugin API source JAR, got: ${'$'}sourceFiles"
                        }
                    }
                }
                """.trimIndent()

        build("verifyBundledPluginSources")
    }

    @Test
    fun `local plugin API sources are attached without entering project classpaths`() {
        val pluginArchive = dir.resolve("plugin-a-1.0.0.zip")
        writeLocalPluginWithApiSources(pluginArchive)

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        localPlugin(file("${pluginArchive.invariantSeparatorsPathString}"))
                    }
                }

                tasks.register("verifyLocalPluginApiSources") {
                    doLast {
                        val compileClasspath = configurations.compileClasspath.get()
                        val localPlugin = compileClasspath.incoming.resolutionResult.allComponents
                            .map { it.id }
                            .filterIsInstance<org.gradle.api.artifacts.component.ModuleComponentIdentifier>()
                            .single { it.group == "localPlugin" }
                        val sourceFiles = dependencies.createArtifactResolutionQuery()
                            .forComponents(localPlugin)
                            .withArtifacts(
                                org.gradle.jvm.JvmLibrary::class.java,
                                org.gradle.language.base.artifact.SourcesArtifact::class.java,
                            )
                            .execute()
                            .resolvedComponents
                            .flatMap { component ->
                                component.getArtifacts(org.gradle.language.base.artifact.SourcesArtifact::class.java)
                                    .filterIsInstance<org.gradle.api.artifacts.result.ResolvedArtifactResult>()
                                    .map { it.file.name }
                            }

                        check(sourceFiles == listOf("plugin-a-api-sources.jar")) {
                            "Expected the local plugin API source JAR, got: ${'$'}sourceFiles"
                        }

                        val projectClasspaths = compileClasspath.files + configurations.testRuntimeClasspath.get().files
                        check(projectClasspaths.none { it.name == "plugin-a-api-sources.jar" }) {
                            "Plugin API source JAR leaked onto a project classpath"
                        }
                    }
                }
                """.trimIndent()

        build("verifyLocalPluginApiSources")

        // Keep the generated Ivy descriptor but remove the extracted ZIP to exercise extraction while the
        // configuration-cache entry is stored. Delete tolerantly: the classpath resolved above may still keep the
        // extracted JARs locked in the Gradle daemon (memory-mapped on Windows), so a fire-and-forget delete would
        // leave locked files behind. ExtractorService now skips re-extraction over an already-materialized directory,
        // so any file that stays locked is harmless rather than overwritten (which used to hang on Windows).
        deleteRecursivelyTolerating(dir.resolve(".intellijPlatform/extracted-plugins"))
        val configurationCacheArguments = listOf("--configuration", "compileClasspath")
        buildWithConfigurationCache("dependencies", args = configurationCacheArguments)
        buildWithConfigurationCache("dependencies", args = configurationCacheArguments) {
            assertContains("Reusing configuration cache.", output)
        }
    }

    @Test
    @Ignore("When using cache, this warning is not emitted.")
    fun `warn when using Rider with useInstaller true`() {
        gradleProperties write //language=properties
                """
                intellijPlatform.type=RD
                """.trimIndent()

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            assertContains("Using Rider as a target IntelliJ Platform with `useInstaller = true` is currently not supported, please set `useInstaller = false` instead.", output)
        }
    }

    @Test
    fun `do not warn when using Rider with useInstaller false`() {
        gradleProperties write //language=properties
                """
                intellijPlatform.type=RD
                intellijPlatform.useInstaller=false
                """.trimIndent()

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            assertNotContains("Using Rider as a target IntelliJ Platform with `useInstaller = true` is currently not supported, please set `useInstaller = false` instead.", output)
        }
    }

    @Test
    fun `create frontend dependency resolves JetBrains Client installer`() {
        val buildNumber = "251.28774.11"
        val artifact = currentJetBrainsClientArtifact(buildNumber)
        val repository = dir.resolve("jetbrains-client-repository")
        val artifactPath = repository.resolve("idea/code-with-me/${artifact.fileName}")
        writeJetBrainsClientArchive(artifactPath, artifact.extension, buildNumber)

        buildFile overwrite //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform")
                }

                repositories {
                    ivy {
                        url = uri("${repository.invariantSeparatorsPathString}")
                        patternLayout {
                            artifact("[organization]/[module]-[revision](-[classifier]).[ext]")
                            artifact("[organization]/[module]-[revision](.[classifier]).[ext]")
                        }
                        metadataSources {
                            artifact()
                        }
                        content {
                            includeModule("idea/code-with-me", "JetBrainsClient")
                        }
                    }

                    intellijPlatform {
                        localPlatformArtifacts()
                    }
                }

                dependencies {
                    intellijPlatform {
                        create("IU", "2025.1.6") {
                            productMode.set(org.jetbrains.intellij.platform.gradle.ProductMode.FRONTEND)
                        }
                    }
                }
                """.trimIndent()

        build(
            "dependencies",
            "--configuration",
            "intellijPlatformLocal",
            projectProperties = mapOf(
                GradleProperties.ProductsReleasesCdnBuildsUrl.toString() to resourceUrl("products-releases/jetbrains-product-releases-IC.json").toString().replace("IC.json", "{type}.json"),
            ),
        ) {
            assertContains("localIde:JBC:JBC-$buildNumber", output)
        }
    }

    @Test
    fun `testFramework excludes bundled IntelliJ Platform modules from transitive dependencies`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        testFramework(TestFrameworkType.Platform)
                    }
                }
                """.trimIndent()

        build("dependencies", "--configuration=intellijPlatformTestDependencies") {
            assertContains("com.jetbrains.intellij.platform:test-framework", output)
            assertNotContains("com.jetbrains.intellij.platform:boot", output)
            assertNotContains("com.jetbrains.intellij.platform:util", output)
            assertNotContains("com.jetbrains.intellij.platform:lang-impl", output)
            assertNotContains("com.jetbrains.intellij.platform:ide-impl", output)
            assertNotContains("com.jetbrains.intellij.platform:core-ui", output)
        }
    }

    @Test
    fun `testFrameworks adds all requested test framework dependencies`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        testFrameworks(TestFrameworkType.Platform, TestFrameworkType.JUnit5)
                        testFrameworks(listOf(TestFrameworkType.Plugin.Java, TestFrameworkType.Plugin.Maven))
                    }
                }
                """.trimIndent()

        build("dependencies", "--configuration=intellijPlatformTestDependencies") {
            assertContains("com.jetbrains.intellij.platform:test-framework", output)
            assertContains("com.jetbrains.intellij.platform:test-framework-junit5", output)
            assertContains("com.jetbrains.intellij.java:java-test-framework", output)
            assertContains("com.jetbrains.intellij.maven:maven-test-framework", output)
        }
    }

    @Test
    fun `latest resolves the newest installer for the exact requested type`() {
        val properties = productReleasesProperties + mapOf(
            "intellijPlatform.type" to "IC",
            "intellijPlatform.version" to Constraints.LATEST_VERSION,
        )

        buildWithConfigurationCache(
            "dependencies",
            "--configuration=intellijPlatformDependencyArchive",
            projectProperties = properties,
        ) {
            assertContains("idea:ideaIC:2025.2.6.2", output)
            assertNotContains("idea:idea:262.8665.81", output)
        }

        buildWithConfigurationCache(
            "dependencies",
            "--configuration=intellijPlatformDependencyArchive",
            projectProperties = properties,
        ) {
            assertContains("Reusing configuration cache.", output)
            assertContains("idea:ideaIC:2025.2.6.2", output)
        }
    }

    @Test
    fun `latest accepts the IU string code and selects the newest release across all channels`() {
        build(
            "dependencies",
            "--configuration=intellijPlatformDependencyArchive",
            projectProperties = productReleasesProperties + mapOf(
                "intellijPlatform.type" to "IU",
                "intellijPlatform.version" to Constraints.LATEST_VERSION,
            ),
        ) {
            assertContains("idea:idea:262.8665.81", output)
            assertNotContains("idea:ideaIU:", output)
        }
    }

    @Test
    fun `latest rejects non-installer platform dependencies`() {
        buildAndFail(
            "dependencies",
            "--configuration=intellijPlatformDependencyArchive",
            projectProperties = productReleasesProperties + mapOf(
                "intellijPlatform.type" to "IC",
                "intellijPlatform.version" to Constraints.LATEST_VERSION,
                "intellijPlatform.useInstaller" to "false",
            ),
        ) {
            assertContains("The 'latest' IntelliJ Platform version can only be used with installer distributions. Set `useInstaller = true`.", output)
        }
    }

    @Test
    fun `latest is normalized before naming the IDE cache directory`() {
        val cachedIde = idesCacheDir.resolve("IC-2025.2.6.2")
        cachedIde.resolve("product-info.json") overwrite //language=json
                """
                {
                    "name": "IntelliJ IDEA Community Edition",
                    "version": "2025.2.6.2",
                    "buildNumber": "252.28539.54",
                    "productCode": "IC"
                }
                """.trimIndent()
        cachedIde.resolve("cache-marker") overwrite "cached"

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    caching {
                        ides {
                            enabled = true
                            path = File("${idesCacheDir.invariantSeparatorsPathString}")
                        }
                    }
                }
                """.trimIndent()

        build(
            "dependencies",
            "--configuration=intellijPlatformLocal",
            projectProperties = productReleasesProperties + mapOf(
                "intellijPlatform.type" to "IC",
                "intellijPlatform.version" to Constraints.LATEST_VERSION,
            ),
        ) {
            assertContains("localIde:IC:IC-252.28539.54", output)
        }

        kotlin.test.assertFalse(idesCacheDir.resolve("IC-latest").toFile().exists())
    }

    private val productReleasesProperties
        get() = mapOf(
            GradleProperties.ProductsReleasesCdnBuildsUrl.toString() to
                    resourceUrl("products-releases/jetbrains-product-releases-IC.json").toString().replace("IC.json", "{type}.json"),
        )

    private fun currentJetBrainsClientArtifact(buildNumber: String): JetBrainsClientArtifact {
        val arch = System.getProperty("os.arch").takeIf { it == "aarch64" }

        return with(OperatingSystem.current()) {
            when {
                isLinux -> JetBrainsClientArtifact(
                    fileName = "JetBrainsClient-$buildNumber${arch?.let { "-$it" }.orEmpty()}.tar.gz",
                    extension = "tar.gz",
                )

                isWindows -> {
                    val classifier = when (arch) {
                        null -> "jbr.win"
                        else -> "$arch.jbr.win"
                    }
                    JetBrainsClientArtifact(
                        fileName = "JetBrainsClient-$buildNumber.$classifier.zip",
                        extension = "zip",
                    )
                }

                isMacOsX -> JetBrainsClientArtifact(
                    fileName = "JetBrainsClient-$buildNumber${arch?.let { "-$it" }.orEmpty()}.sit",
                    extension = "sit",
                )

                else -> error("Unsupported operating system: $name")
            }
        }
    }

    private fun writeJetBrainsClientArchive(path: Path, extension: String, buildNumber: String) {
        val productInfo = """
            {
                "name": "JetBrains Client",
                "version": "2025.1.6",
                "buildNumber": "$buildNumber",
                "productCode": "JBC"
            }
        """.trimIndent().toByteArray()

        path.parent.createDirectories()
        when (extension) {
            "tar.gz" -> writeTarGz(path, "product-info.json", productInfo)
            else -> writeZip(path, "product-info.json", productInfo)
        }
    }

    private fun writeLocalPluginWithApiSources(path: Path) {
        val pluginXml = //language=xml
            """
            <idea-plugin>
                <id>com.example.plugin-a</id>
                <name>Plugin A</name>
                <version>1.0.0</version>
                <vendor>Test</vendor>
                <idea-version since-build="241" />
            </idea-plugin>
            """.trimIndent().toByteArray()
        val pluginJar = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("META-INF/plugin.xml"))
                zip.write(pluginXml)
                zip.closeEntry()
            }
            bytes.toByteArray()
        }
        val sourceJar = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { }
            bytes.toByteArray()
        }

        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putNextEntry(ZipEntry("plugin-a/lib/plugin-a.jar"))
            zip.write(pluginJar)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("plugin-a/lib/src/plugin-a-api-sources.jar"))
            zip.write(sourceJar)
            zip.closeEntry()
        }
    }

    /**
     * Deletes [path] recursively, tolerating files that cannot be removed yet. The Gradle daemon may still keep the
     * just-resolved classpath JARs open (memory-mapped and therefore locked on Windows), so a single fire-and-forget
     * delete would silently leave locked files behind. Retrying a few times gives the daemon a chance to release the
     * handles; whatever remains locked afterwards is left in place instead of failing the test.
     */
    private fun deleteRecursivelyTolerating(path: Path, attempts: Int = 10) {
        val file = path.toFile()
        repeat(attempts) {
            if (!file.exists() || file.deleteRecursively()) {
                return
            }
            Thread.sleep(200)
        }
    }

    private fun writeBundledPluginIvyFixture(platformPath: Path, ivyPath: Path) {
        platformPath.resolve("product-info.json") overwrite //language=json
                """
                {
                    "name": "IntelliJ IDEA Community Edition",
                    "version": "2024.1",
                    "buildNumber": "241.1",
                    "productCode": "IC"
                }
                """.trimIndent()

        listOf(
            platformPath.resolve("plugins/plugin-a/lib/plugin-a.jar"),
            platformPath.resolve("plugins/plugin-a/lib/src/plugin-a-api-sources.jar"),
        ).forEach { jar ->
            jar.parent.createDirectories()
            ZipOutputStream(Files.newOutputStream(jar)).use { }
        }

        ivyPath.resolve("IC-241.1/bundledPlugin-plugin-a-IC-241.1.xml") overwrite //language=xml
                """
                <ivy-module version="2.0">
                    <info organisation="bundledPlugin" module="plugin-a" revision="IC-241.1" />
                    <configurations>
                        <conf name="default" visibility="public" />
                        <conf name="sources" visibility="public" />
                    </configurations>
                    <publications>
                        <artifact name="plugin-a" ext="jar" conf="default" url="plugins/plugin-a/lib" />
                        <artifact name="plugin-a-api-sources" ext="jar" conf="sources" url="plugins/plugin-a/lib/src" />
                    </publications>
                </ivy-module>
                """.trimIndent()
    }

    private fun writeZip(path: Path, name: String, content: ByteArray) {
        ZipOutputStream(Files.newOutputStream(path)).use {
            it.putNextEntry(ZipEntry(name))
            it.write(content)
            it.closeEntry()
        }
    }

    private fun writeTarGz(path: Path, name: String, content: ByteArray) {
        GZIPOutputStream(Files.newOutputStream(path)).use { gzip ->
            val header = ByteArray(512)
            fun write(offset: Int, length: Int, value: String) {
                value.toByteArray().copyInto(header, offset, endIndex = minOf(value.length, length))
            }

            write(0, 100, name)
            write(100, 8, "0000644")
            write(108, 8, "0000000")
            write(116, 8, "0000000")
            write(124, 12, content.size.toString(8).padStart(11, '0'))
            write(136, 12, "00000000000")
            repeat(8) { header[148 + it] = ' '.code.toByte() }
            header[156] = '0'.code.toByte()
            write(257, 6, "ustar")
            write(263, 2, "00")

            val checksum = header.sumOf { it.toUByte().toInt() }
            write(148, 6, checksum.toString(8).padStart(6, '0'))
            header[154] = 0
            header[155] = ' '.code.toByte()

            gzip.write(header)
            gzip.write(content)
            gzip.write(ByteArray((512 - content.size % 512) % 512))
            gzip.write(ByteArray(1024))
        }
    }

    private data class JetBrainsClientArtifact(
        val fileName: String,
        val extension: String,
    )
}
