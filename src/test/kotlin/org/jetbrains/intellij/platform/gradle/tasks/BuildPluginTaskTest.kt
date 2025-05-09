// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import java.util.jar.Manifest
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.*

class BuildPluginTaskTest : IntelliJPluginTestBase() {

    @BeforeTest
    override fun setup() {
        super.setup()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    buildSearchableOptions = true
                }
                """.trimIndent()
    }

    @Test
    fun `build plugin distribution`() {
        writeJavaFile()

        dir.resolve("src/main/resources/META-INF/other.xml") write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        dir.resolve("src/main/resources/META-INF/nonIncluded.xml") write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        pluginXml write //language=xml
                """
                <idea-plugin>
                  <name>MyPluginName</name>
                  <vendor>JetBrains</vendor>
                  <depends config-file="other.xml" />
                </idea-plugin>
                """.trimIndent()

        buildFile write //language=kotlin
                """
                dependencies {
                    implementation("joda-time:joda-time:2.8.1")
                    
                    intellijPlatform {
                        bundledPlugin("com.intellij.copyright")
                    }
                }
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN)

        buildDirectory.resolve("distributions/projectName-1.0.0.zip").let { distribution ->
            assertExists(distribution)

            distribution.toZip().use { zip ->
                assertEquals(
                    setOf(
                        "projectName/",
                        "projectName/lib/",
                        "projectName/lib/joda-time-2.8.1.jar",
                        "projectName/lib/projectName-1.0.0.jar",
                        "projectName/lib/projectName-1.0.0-searchableOptions.jar",
                    ),
                    collectPaths(zip)
                )

                zip.extract("projectName/lib/projectName-1.0.0.jar").toZip().use { jar ->
                    assertEquals(
                        setOf(
                            "App.class",
                            "META-INF/",
                            "META-INF/MANIFEST.MF",
                            "META-INF/nonIncluded.xml",
                            "META-INF/other.xml",
                            "META-INF/plugin.xml",
                        ),
                        collectPaths(jar)
                    )

                    assertEquals(
                        """
                        <idea-plugin>
                          <idea-version since-build="223.8836" />
                          <version>1.0.0</version>
                          <name>MyPluginName</name>
                          <vendor>JetBrains</vendor>
                          <depends config-file="other.xml" />
                        </idea-plugin>
                        """.trimIndent(),
                        fileText(jar, "META-INF/plugin.xml"),
                    )
                }
            }
        }
    }

    @Test
    fun `build plugin distribution with Kotlin`() {
        writeJavaFile()
        writeKotlinUIFile()

        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>MyPluginName</name>
                    <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN)

        buildDirectory.resolve("distributions/projectName-1.0.0.zip").let { distribution ->
            assertExists(distribution)

            distribution.toZip().use { zip ->
                assertEquals(
                    setOf(
                        "projectName/",
                        "projectName/lib/",
                        "projectName/lib/projectName-1.0.0.jar",
                        "projectName/lib/projectName-1.0.0-searchableOptions.jar",
                    ),
                    collectPaths(zip),
                )

                zip.extract("projectName/lib/projectName-1.0.0.jar").toZip().use { jar ->
                    assertEquals(
                        setOf(
                            "App.class",
                            "pack/",
                            "pack/AppKt.class",
                            "META-INF/",
                            "META-INF/MANIFEST.MF",
                            "META-INF/plugin.xml",
                            "META-INF/projectName.kotlin_module",
                        ),
                        collectPaths(jar),
                    )
                }
            }
        }
    }

    @Test
    fun `use custom sandbox for distribution`() {
        writeJavaFile()

        dir.resolve("src/main/resources/META-INF/other.xml") write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        dir.resolve("src/main/resources/META-INF/nonIncluded.xml") write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>MyPluginName</name>
                    <vendor>JetBrains</vendor>
                    <depends config-file="other.xml" />
                </idea-plugin>
                """.trimIndent()

        val sandboxPath = dir.resolve("customSandbox").invariantSeparatorsPathString
        buildFile write //language=kotlin
                """
                dependencies {
                    implementation("joda-time:joda-time:2.8.1")
                    intellijPlatform {
                        bundledPlugin("com.intellij.copyright")
                    }
                }
                
                intellijPlatform { 
                    sandboxContainer = file("$sandboxPath")
                }
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN)

        buildDirectory.resolve("distributions/projectName-1.0.0.zip").let { distribution ->
            assertExists(distribution)

            distribution.toZip().use { zip ->
                assertEquals(
                    setOf(
                        "projectName/",
                        "projectName/lib/",
                        "projectName/lib/joda-time-2.8.1.jar",
                        "projectName/lib/projectName-1.0.0.jar",
                        "projectName/lib/projectName-1.0.0-searchableOptions.jar",
                    ),
                    collectPaths(zip)
                )
            }
        }
    }

    @Test
    fun `use gradle project name for distribution if plugin name is not defined`() {
        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>MyPluginName</name>
                    <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN)

        assertEquals(
            setOf("projectName-1.0.0.zip"),
            buildDirectory.resolve("distributions").toFile().list()?.toSet(),
        )
    }

    @Test
    fun `can compile classes that depends on external plugins`() {
        dir.resolve("src/main/java/App.java") write //language=java
                """
                import java.lang.String;
                import org.jetbrains.annotations.NotNull;
                import org.intellij.plugins.markdown.lang.MarkdownLanguage;
                
                class App {
                    public static void main(@NotNull String[] strings) {
                        System.out.println(MarkdownLanguage.INSTANCE.getDisplayName());
                    }
                }
                """.trimIndent()

        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>MyPluginName</name>
                    <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent()

        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        marketplace()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        plugin("org.intellij.plugins.markdown", "$markdownPluginVersion")
                    }
                }
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN)

        buildDirectory.resolve("distributions/projectName-1.0.0.zip").let { distribution ->
            assertExists(distribution)

            distribution.toZip().use { zip ->
                zip.extract("projectName/lib/projectName-1.0.0.jar").toZip().use { jar ->
                    assertTrue(collectPaths(jar).contains("App.class"))
                }
            }
        }
    }

    @Test
    fun `can compile classes that depend on external plugin with classes directory`() {
        dir.resolve("src/main/java/App.java") write //language=java
                """
                import java.lang.String;
                import org.jetbrains.annotations.NotNull;
                import org.asciidoc.intellij.AsciiDocPlugin;
                
                class App {
                    public static void main(@NotNull String[] strings) {
                        System.out.println(AsciiDocPlugin.PLUGIN_ID);
                    }
                }
                """.trimIndent()

        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>MyPluginName</name>
                    <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent()

        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        marketplace()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        plugin("org.asciidoctor.intellij.asciidoc", "0.39.11")
                    }
                }
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN)

        buildDirectory.resolve("distributions/projectName-1.0.0.zip").let { distribution ->
            assertExists(distribution)

            distribution.toZip().use { zip ->
                zip.extract("projectName/lib/projectName-1.0.0.jar").toZip().use { jar ->
                    assertTrue(collectPaths(jar).contains("App.class"))
                }
            }
        }
    }

    @Test
    fun `build plugin without sources`() {
        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>MyPluginName</name>
                    <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN)

        buildDirectory.resolve("distributions/projectName-1.0.0.zip").let { distribution ->
            assertExists(distribution)

            distribution.toZip().use { zip ->
                assertEquals(
                    setOf(
                        "projectName/",
                        "projectName/lib/",
                        "projectName/lib/projectName-1.0.0.jar",
                        "projectName/lib/projectName-1.0.0-searchableOptions.jar",
                    ),
                    collectPaths(zip)
                )

                zip.extract("projectName/lib/projectName-1.0.0.jar").toZip().use { jar ->
                    assertEquals(
                        setOf(
                            "META-INF/",
                            "META-INF/MANIFEST.MF",
                            "META-INF/plugin.xml",
                        ),
                        collectPaths(jar)
                    )
                }
            }
        }
    }

    @Test
    fun `include only relevant searchableOptions_jar`() {
        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>MyPluginName</name>
                    <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN)

        buildFile write //language=kotlin
                """
                version = "1.0.1"
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN)

        buildDirectory.resolve("distributions/projectName-1.0.0.zip").let { distribution ->
            assertExists(distribution)

            distribution.toZip().use { zip ->
                assertEquals(
                    setOf(
                        "projectName/",
                        "projectName/lib/",
                        "projectName/lib/projectName-1.0.0.jar",
                        "projectName/lib/projectName-1.0.0-searchableOptions.jar",
                    ),
                    collectPaths(zip)
                )

                zip.extract("projectName/lib/projectName-1.0.0.jar").toZip().use { jar ->
                    assertEquals(
                        setOf(
                            "META-INF/",
                            "META-INF/MANIFEST.MF",
                            "META-INF/plugin.xml",
                        ),
                        collectPaths(jar)
                    )
                }
            }
        }
    }

    @Test
    fun `provide MANIFEST_MF with build details`() {
        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>MyPluginName</name>
                    <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN)

        buildDirectory.resolve("distributions").resolve("projectName-1.0.0.zip").let { archive ->
            archive.toZip().use { zip ->
                zip.extract("projectName/lib/projectName-1.0.0.jar").toZip().use { jar ->
                    fileText(jar, "META-INF/MANIFEST.MF").byteInputStream().use { Manifest(it).mainAttributes }.let {
                        assertNotNull(it)

                        assertEquals("1.0", it.getValue("Manifest-Version"))
                        assertEquals("Gradle $gradleVersion", it.getValue("Created-By"))
                        assertEquals("1.0.0", it.getValue("Version"))
                        assertEquals(Jvm.current().toString(), it.getValue("Build-JVM"))
                        assertEquals(OperatingSystem.current().toString(), it.getValue("Build-OS"))
                        assertEquals(Plugin.NAME, it.getValue("Build-Plugin"))
                        assertEquals("0.0.0", it.getValue("Build-Plugin-Version"))
                        assertEquals(intellijPlatformType, it.getValue("Platform-Type"))
                        assertEquals(intellijPlatformVersion, it.getValue("Platform-Version"))
                        assertEquals("223.8836.41", it.getValue("Platform-Build"))
                        assertEquals("false", it.getValue("Kotlin-Stdlib-Bundled"))
                        assertEquals(null, it.getValue("Kotlin-Version"))
                    }
                }
            }
        }
    }
}
