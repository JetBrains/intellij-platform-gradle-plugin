// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import java.util.jar.Manifest
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("GroovyUnusedAssignment", "PluginXmlValidity")
class BuildPluginTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `build plugin distribution`() {
        writeJavaFile()

        file("src/main/resources/META-INF/other.xml").xml("<idea-plugin />")

        file("src/main/resources/META-INF/nonIncluded.xml").xml("<idea-plugin />")

        pluginXml.xml(
            """
            <idea-plugin>
              <name>MyPluginName</name>
              <vendor>JetBrains</vendor>
              <depends config-file="other.xml" />
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            
            intellij { 
                pluginName = 'myPluginName' 
                plugins = ['copyright']
            }
            dependencies { 
                implementation 'joda-time:joda-time:2.8.1'
            }
            buildSearchableOptions {
                enabled = true
            }
            """.trimIndent()
        )

        build(Tasks.BUILD_PLUGIN)

        val distribution = buildDirectory.resolve("distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())

        val zip = distribution.toZip()

        assertEquals(
            setOf(
                "myPluginName/",
                "myPluginName/lib/",
                "myPluginName/lib/joda-time-2.8.1.jar",
                "myPluginName/lib/projectName-0.42.123.jar",
                "myPluginName/lib/searchableOptions-0.42.123.jar",
            ),
            collectPaths(zip)
        )

        val jar = zip.extract("myPluginName/lib/projectName-0.42.123.jar").toZip()
        assertEquals(
            setOf(
                "App.class",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/nonIncluded.xml",
                "META-INF/other.xml",
                "META-INF/plugin.xml",
            ),
            collectPaths(zip)
        )

        assertEquals(
            """
            <idea-plugin>
              <version>0.42.123</version>
              <idea-version since-build="221.6008" until-build="221.*" />
              <name>MyPluginName</name>
              <vendor>JetBrains</vendor>
              <depends config-file="other.xml" />
            </idea-plugin>
            """.trimIndent(),
            fileText(jar, "META-INF/plugin.xml"),
        )
    }

    @Test
    fun `build plugin distribution with Kotlin`() {
        writeJavaFile()
        writeKotlinUIFile()

        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123' 
            
            intellij {
                pluginName = 'myPluginName'
            }
            buildSearchableOptions {
                enabled = true
            }
            """.trimIndent()
        )

        gradleProperties.properties(
            """
            kotlin.incremental.useClasspathSnapshot = false
            """.trimIndent()
        )

        build(Tasks.BUILD_PLUGIN)

        val distribution = buildDirectory.resolve("distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())

        val zip = distribution.toZip()
        assertEquals(
            setOf(
                "myPluginName/",
                "myPluginName/lib/",
                "myPluginName/lib/projectName-0.42.123.jar",
                "myPluginName/lib/searchableOptions-0.42.123.jar",
            ),
            collectPaths(zip),
        )

        val jar = zip.extract("myPluginName/lib/projectName-0.42.123.jar").toZip()
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

    @Test
    fun `use custom sandbox for distribution`() {
        writeJavaFile()

        file("src/main/resources/META-INF/other.xml").xml("<idea-plugin />")

        file("src/main/resources/META-INF/nonIncluded.xml").xml("<idea-plugin />")

        pluginXml.xml(
            """
            <idea-plugin >
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
                <depends config-file="other.xml" />
            </idea-plugin>
            """.trimIndent()
        )

        val sandboxPath = adjustWindowsPath("${dir.pathString}/customSandbox")
        buildFile.groovy(
            """
            version = '0.42.123'
            
            dependencies { 
                implementation 'joda-time:joda-time:2.8.1'
            }
            
            intellij { 
                pluginName = 'myPluginName' 
                plugins = ['copyright'] 
                sandboxDir = '$sandboxPath'
            }
            
            buildSearchableOptions {
                enabled = true
            }
            """.trimIndent()
        )

        build(Tasks.BUILD_PLUGIN)

        val distribution = buildDirectory.resolve("distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())
        val zip = distribution.toZip()

        assertEquals(
            setOf(
                "myPluginName/",
                "myPluginName/lib/",
                "myPluginName/lib/joda-time-2.8.1.jar",
                "myPluginName/lib/projectName-0.42.123.jar",
                "myPluginName/lib/searchableOptions-0.42.123.jar",
            ),
            collectPaths(zip)
        )
    }

    @Test
    fun `use gradle project name for distribution if plugin name is not defined`() {
        buildFile.groovy(
            """
            version = '0.42.123'
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        build(Tasks.BUILD_PLUGIN)

        assertEquals(
            setOf(
                "projectName-0.42.123.zip",
            ),
            buildDirectory.resolve("distributions").toFile().list()?.toSet(),
        )
    }

    @Test
    fun `can compile classes that depends on external plugins`() {
        file("src/main/java/App.java").java(
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
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            
            intellij {
                pluginName = 'myPluginName'
                plugins = ['org.intellij.plugins.markdown:$testMarkdownPluginVersion']
            }
            """.trimIndent()
        )

        build(Tasks.BUILD_PLUGIN)

        val distribution = buildDirectory.resolve("distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())

        val zip = distribution.toZip()
        val jar = zip.extract("myPluginName/lib/projectName-0.42.123.jar").toZip()
        assertTrue(collectPaths(jar).contains("App.class"))
    }

    @Test
    fun `can compile classes that depend on external plugin with classes directory`() {
        file("src/main/java/App.java").java(
            """
            import java.lang.String;
            import org.jetbrains.annotations.NotNull;
            import org.asciidoc.intellij.AsciiDoc;
            
            class App {
                public static void main(@NotNull String[] strings) {
                    System.out.println(AsciiDoc.class.getName());
                }
            }
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            
            intellij {
                pluginName = 'myPluginName'
                plugins = ['org.asciidoctor.intellij.asciidoc:0.20.6']
            }
            """.trimIndent()
        )

        build(Tasks.BUILD_PLUGIN)

        val distribution = buildDirectory.resolve("distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())

        val zip = distribution.toZip()
        val jar = zip.extract("myPluginName/lib/projectName-0.42.123.jar").toZip()
        assertTrue(collectPaths(jar).contains("App.class"))
    }

    @Test
    fun `build plugin without sources`() {
        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            
            intellij {
                pluginName = 'myPluginName'
            } 
            buildSearchableOptions {
                enabled = true
            }
            """.trimIndent()
        )

        build(Tasks.BUILD_PLUGIN)

        val distribution = buildDirectory.resolve("distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())

        val zip = distribution.toZip()
        assertEquals(
            setOf(
                "myPluginName/",
                "myPluginName/lib/",
                "myPluginName/lib/projectName-0.42.123.jar",
                "myPluginName/lib/searchableOptions-0.42.123.jar",
            ),
            collectPaths(zip)
        )

        val jar = zip.extract("myPluginName/lib/projectName-0.42.123.jar").toZip()
        assertEquals(
            setOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/plugin.xml",
            ),
            collectPaths(jar)
        )
    }

    @Test
    fun `include only relevant searchableOptions_jar`() {
        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.321'
            
            intellij {
                pluginName = 'myPluginName'
            }
            """.trimIndent()
        )

        build(Tasks.BUILD_PLUGIN)

        buildFile.groovy(
            """
            version = '0.42.123'
            
            intellij {
                pluginName = 'myPluginName'
            }
            buildSearchableOptions {
                enabled = true
            }
            """.trimIndent()
        )

        build(Tasks.BUILD_PLUGIN)

        val distribution = buildDirectory.resolve("distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())

        val zip = distribution.toZip()
        assertEquals(
            setOf(
                "myPluginName/",
                "myPluginName/lib/",
                "myPluginName/lib/projectName-0.42.123.jar",
                "myPluginName/lib/searchableOptions-0.42.123.jar",
            ),
            collectPaths(zip)
        )

        val jar = zip.extract("myPluginName/lib/projectName-0.42.123.jar").toZip()
        assertEquals(
            setOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/plugin.xml",
            ),
            collectPaths(jar)
        )
    }

    @Test
    fun `provide MANIFEST_MF with build details`() {
        buildFile.groovy(
            """
            version = '0.42.123'
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        build(Tasks.BUILD_PLUGIN)

        val archive = buildDirectory.resolve("distributions").resolve("projectName-0.42.123.zip")
        val artifact = archive.toZip().extract("projectName/lib/projectName-0.42.123.jar").toZip()
        fileText(artifact, "META-INF/MANIFEST.MF").byteInputStream().use { Manifest(it).mainAttributes }.let {
            assertNotNull(it)

            assertEquals("1.0", it.getValue("Manifest-Version"))
            assertEquals("Gradle $gradleVersion", it.getValue("Created-By"))
            assertEquals(Jvm.current().toString(), it.getValue("Build-JVM"))
            assertEquals("0.42.123", it.getValue("Version"))
            assertEquals(PLUGIN_NAME, it.getValue("Build-Plugin"))
            assertEquals("0.0.0", it.getValue("Build-Plugin-Version"))
            assertEquals(OperatingSystem.current().toString(), it.getValue("Build-OS"))
        }
    }
}
