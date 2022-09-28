// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_NAME
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.io.File
import java.util.jar.Manifest
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("GroovyUnusedAssignment", "PluginXmlValidity", "ComplexRedundantLet")
class BuildPluginTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `build plugin distribution`() {
        writeJavaFile()

        file("src/main/resources/META-INF/other.xml").xml(
            """
            <idea-plugin />
            """
        )

        file("src/main/resources/META-INF/nonIncluded.xml").xml(
            """
            <idea-plugin />
            """
        )

        pluginXml.xml(
            """
            <idea-plugin>
              <name>MyPluginName</name>
              <vendor>JetBrains</vendor>
              <depends config-file="other.xml" />
            </idea-plugin>
            """
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
            """
        )

        build(BUILD_PLUGIN_TASK_NAME)

        val distribution = File(buildDirectory, "distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())

        val zipFile = ZipFile(distribution)
        assertEquals(
            setOf(
                "myPluginName/",
                "myPluginName/lib/",
                "myPluginName/lib/joda-time-2.8.1.jar",
                "myPluginName/lib/projectName-0.42.123.jar",
                "myPluginName/lib/searchableOptions-0.42.123.jar",
            ),
            collectPaths(zipFile)
        )

        val jar = ZipFile(extractFile(zipFile, "myPluginName/lib/projectName-0.42.123.jar"))
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
              <version>0.42.123</version>
              <idea-version since-build="212.5712" until-build="212.*" />
              <name>MyPluginName</name>
              <vendor>JetBrains</vendor>
              <depends config-file="other.xml" />
            </idea-plugin>
            """.trimIndent(),
            fileText(jar, "META-INF/plugin.xml"),
        )
    }

    @Test
    fun `build plugin distribution with Kotlin 1_1_4`() {
        writeJavaFile()
        writeKotlinUIFile()

        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """
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
            """
        )

        build(BUILD_PLUGIN_TASK_NAME)

        val distribution = File(buildDirectory, "distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())

        val zipFile = ZipFile(distribution)
        assertEquals(
            setOf(
                "myPluginName/",
                "myPluginName/lib/",
                "myPluginName/lib/projectName-0.42.123.jar",
                "myPluginName/lib/searchableOptions-0.42.123.jar",
            ),
            collectPaths(zipFile),
        )

        val jar = ZipFile(extractFile(zipFile, "myPluginName/lib/projectName-0.42.123.jar"))
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

        file("src/main/resources/META-INF/other.xml").xml(
            """
            <idea-plugin />
            """
        )

        file("src/main/resources/META-INF/nonIncluded.xml").xml(
            """
            <idea-plugin />
            """
        )

        pluginXml.xml(
            """
            <idea-plugin >
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
                <depends config-file="other.xml" />
            </idea-plugin>
            """
        )

        val sandboxPath = adjustWindowsPath("${dir.canonicalPath}/customSandbox")
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
            """
        )

        build(BUILD_PLUGIN_TASK_NAME)

        val distribution = File(buildDirectory, "distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())

        assertEquals(
            setOf(
                "myPluginName/",
                "myPluginName/lib/",
                "myPluginName/lib/joda-time-2.8.1.jar",
                "myPluginName/lib/projectName-0.42.123.jar",
                "myPluginName/lib/searchableOptions-0.42.123.jar",
            ),
            collectPaths(ZipFile(distribution))
        )
    }

    @Test
    fun `use gradle project name for distribution if plugin name is not defined`() {
        buildFile.groovy(
            """
            version = '0.42.123'
            """
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """
        )

        build(BUILD_PLUGIN_TASK_NAME)

        assertEquals(
            setOf(
                "projectName-0.42.123.zip",
            ),
            File(buildDirectory, "distributions").list()?.toSet(),
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
            """
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """
        )

        buildFile.groovy(
            """
            version = '0.42.123'

            intellij {
                pluginName = 'myPluginName'
                plugins = ['org.intellij.plugins.markdown:$testMarkdownPluginVersion']
            }
            """
        )

        build(BUILD_PLUGIN_TASK_NAME)

        val distribution = File(buildDirectory, "distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())

        val jar = extractFile(ZipFile(distribution), "myPluginName/lib/projectName-0.42.123.jar")
        assertTrue(collectPaths(ZipFile(jar)).contains("App.class"))
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
            """
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            
            intellij {
                pluginName = 'myPluginName'
                plugins = ['org.asciidoctor.intellij.asciidoc:0.20.6']
            }
            """
        )

        build(BUILD_PLUGIN_TASK_NAME)

        val distribution = File(buildDirectory, "distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())

        val jar = extractFile(ZipFile(distribution), "myPluginName/lib/projectName-0.42.123.jar")
        assertTrue(collectPaths(ZipFile(jar)).contains("App.class"))
    }

    @Test
    fun `build plugin without sources`() {
        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """
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
            """
        )

        build(BUILD_PLUGIN_TASK_NAME)

        val distribution = File(buildDirectory, "distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())

        val zip = ZipFile(distribution)
        assertEquals(
            setOf(
                "myPluginName/",
                "myPluginName/lib/",
                "myPluginName/lib/projectName-0.42.123.jar",
                "myPluginName/lib/searchableOptions-0.42.123.jar",
            ),
            collectPaths(zip)
        )

        val jar = extractFile(zip, "myPluginName/lib/projectName-0.42.123.jar")
        assertEquals(
            setOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/plugin.xml",
            ),
            collectPaths(ZipFile(jar))
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
            """
        )

        buildFile.groovy(
            """
            version = '0.42.321'
            
            intellij {
                pluginName = 'myPluginName'
            }
            """
        )

        build(BUILD_PLUGIN_TASK_NAME)

        buildFile.groovy(
            """
            version = '0.42.123'
            
            intellij {
                pluginName = 'myPluginName'
            }
            buildSearchableOptions {
                enabled = true
            }
            """
        )

        build(BUILD_PLUGIN_TASK_NAME)

        val distribution = File(buildDirectory, "distributions/myPluginName-0.42.123.zip")
        assertTrue(distribution.exists())

        val zip = ZipFile(distribution)
        assertEquals(
            setOf(
                "myPluginName/",
                "myPluginName/lib/",
                "myPluginName/lib/projectName-0.42.123.jar",
                "myPluginName/lib/searchableOptions-0.42.123.jar",
            ),
            collectPaths(zip)
        )

        val jar = extractFile(zip, "myPluginName/lib/projectName-0.42.123.jar")
        assertEquals(
            setOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/plugin.xml",
            ),
            collectPaths(ZipFile(jar))
        )
    }

    @Test
    fun `provide MANIFEST_MF with build details`() {
        buildFile.groovy(
            """
            version = '0.42.123'
            """
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """
        )

        build(BUILD_PLUGIN_TASK_NAME)

        val archive = buildDirectory.resolve("distributions").resolve("projectName-0.42.123.zip")
        val artifact = extractFile(ZipFile(archive), "projectName/lib/projectName-0.42.123.jar")
        fileText(ZipFile(artifact), "META-INF/MANIFEST.MF").byteInputStream().use { Manifest(it).mainAttributes }.let {
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

    @Test
    fun `reuse configuration cache`() {
        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyPluginName</name>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """
        )

        buildFile.groovy(
            """
            version = '0.42.321'
            
            intellij {
                pluginName = 'myPluginName'
            }
            """
        )

        build(BUILD_PLUGIN_TASK_NAME, "--configuration-cache")
        build(BUILD_PLUGIN_TASK_NAME, "--configuration-cache").let {
            assertContains("Reusing configuration cache.", it.output)
        }
    }
}
