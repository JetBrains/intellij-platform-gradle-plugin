// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.apache.commons.io.FileUtils
import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.net.URL
import kotlin.test.Test

@Suppress("GroovyUnusedAssignment", "PluginXmlValidity")
class RunPluginVerifierTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `run plugin verifier in specified version`() {
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
                verifierVersion = "1.255"
            }
        """)

        val result = build(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        assertContains( "Starting the IntelliJ Plugin Verifier 1.2551", result.output)
    }

    @Test
    fun `run plugin verifier fails on old version lower than 1_255`() {
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
                verifierVersion = "1.254"
            }
        """)

        val result = buildAndFail(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        assertContains( "Could not find org.jetbrains.intellij.plugins:verifier-cli:1.254", result.output)
    }

    @Test
    fun `run plugin verifier in the latest version`() {
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"

            runPluginVerifier {
                ideVersions = ["2020.2.3"]
            }
        """)

        val result = build(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        val version = RunPluginVerifierTask.resolveLatestVersion()
        assertContains( "Starting the IntelliJ Plugin Verifier $version", result.output)
    }

    @Test
    fun `test plugin against two IDEs`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["IC-2020.2.3", "PS-2020.1.3"]
            }
        """)

        val result = build(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        assertContains( "Plugin MyName:1.0.0 against IC-202.7660.26: Compatible", result.output)
        assertContains( "Plugin MyName:1.0.0 against PS-201.8538.41: Compatible", result.output)
    }

    @Test
    fun `test plugin against Android Studio`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["AI-2021.1.1.15"]
            }
        """)

        val result = build(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        assertContains( "Plugin MyName:1.0.0 against AI-211.7628.21.2111.7824002: Compatible", result.output)
    }

    @Test
    fun `set verification reports directory`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"
            
            runPluginVerifier {
                verificationReportsDir = "${'$'}{project.buildDir}/foo"
                ideVersions = ["IC-2020.2.3"]
            }
        """)

        val result = build(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        val directory = file("build/foo").canonicalPath
        assertContains( "Verification reports directory: $directory", result.output)
    }

    @Test
    fun `use ListProductsReleasesTask output on missing ideVersions property`() {
        writeJavaFile()
        writePluginXmlFile()

        val resource = javaClass.classLoader.getResource("products-releases/idea-releases.xml")?.path
        buildFile.groovy("""
            import org.jetbrains.intellij.tasks.ListProductsReleasesTask.Channel

            version = "1.0.0"

            listProductsReleases {
                updatePaths = ['${resource}']
                sinceVersion = "2020.2"
                untilVersion = "2020.2.3"
                releaseChannels = EnumSet.of(Channel.RELEASE)
            }
        """)

        val result = build(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        assertContains( "> Task :listProductsReleases", result.output)
        assertContains( "Starting the IntelliJ Plugin Verifier", result.output)
    }

    @Test
    fun `do not use ListProductsReleasesTask output on empty array passed to ideVersions property`() {
        writeJavaFile()
        writePluginXmlFile()

        buildFile.groovy("""
            version = "1.0.0"

            runPluginVerifier {
                ideVersions = []
                localPaths = [new File('/tmp')]
            }
        """)

        val result = buildAndFail(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        assertContains( "> Task :listProductsReleases SKIPPED", result.output)
    }

    @Test
    fun `fail on verifyPlugin task`() {
        writeJavaFile()
        pluginXml.delete()
        buildFile.groovy("""
            version = "1.0.0"
        """)

        val result = buildAndFail(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        assertContains( "Plugin descriptor 'plugin.xml' is not found", result.output)
        assertContains( "Task :verifyPlugin FAILED", result.output)
    }

    @Test
    fun `fail on Deprecated API usages`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.groovy("""
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                failureLevel = [FailureLevel.DEPRECATED_API_USAGES]
                ideVersions = ["2020.2.3"]
            }
        """)

        val result = buildAndFail(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        assertContains( "Deprecated API usages", result.output)
        assertContains( "org.gradle.api.GradleException: DEPRECATED_API_USAGES", result.output)
    }

    @Test
    fun `pass on Deprecated API usages`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
            }
        """)

        val result = build(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        assertContains( "Deprecated API usages", result.output)
        assertNotContains( "org.gradle.api.GradleException: DEPRECATED_API_USAGES", result.output)
    }

    @Test
    fun `fail on incorrect ideVersion`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.groovy("""
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["foo", "foo", "", "foo"]
            }
        """)

        val result = buildAndFail(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        assertContains( "IDE 'foo' cannot be downloaded.", result.output)
    }

    @Test
    fun `fail on any failureLevel`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.groovy("""
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
                failureLevel = FailureLevel.ALL
            }
        """)

        val result = buildAndFail(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        assertContains( "Deprecated API usages", result.output)
        assertContains( "org.gradle.api.GradleException: DEPRECATED_API_USAGES", result.output)
    }

    @Test
    fun `pass on any failureLevel`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.groovy("""
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
                failureLevel = FailureLevel.NONE
            }
        """)

        val result = build(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME)
        assertContains( "Deprecated API usages", result.output)
        assertNotContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", result.output)
    }

    @Test
    fun `run plugin verifier in offline mode`() {
        writePluginXmlFile()
        warmupGradle()
        buildFile.groovy("""
            version = "1.0.0"

            runPluginVerifier {
                ideVersions = ["2020.1.3"]
                verifierPath = "${'$'}{project.buildDir}/pluginVerifier.jar"
            }
        """)

        val version = RunPluginVerifierTask.resolveLatestVersion()
        URL("${IntelliJPluginConstants.PLUGIN_VERIFIER_REPOSITORY}/org/jetbrains/intellij/plugins/verifier-cli/$version/verifier-cli-$version-all.jar")
            .openStream().use {
                FileUtils.copyInputStreamToFile(it, file("build/pluginVerifier.jar"))
            }

        val result = buildAndFail(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME, "--offline")
        assertContains( "Gradle runs in offline mode.", result.output)
    }

    @Test
    fun `reuse configuration cache`() {
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"

            runPluginVerifier {
                ideVersions = ["2020.2.3"]
            }
        """)

        build(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME, "--configuration-cache")
        val result = build(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME, "--configuration-cache")
        assertContains( "Reusing configuration cache.", result.output)
    }

    private fun warmupGradle() {
        buildFile.groovy("""
            version = "1.0.0"

            runPluginVerifier {
                ideVersions = ["2020.2.3"]
            }
        """)
        build(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
    }

    private fun writeJavaFileWithDeprecation() {
        file("src/main/java/App.java").java("""  
            import java.lang.String;
            import org.jetbrains.annotations.NotNull;
            import com.intellij.openapi.util.text.StringUtil;

            class App {

                public static void main(@NotNull String[] strings) {
                    StringUtil.firstLetterToUpperCase("foo");
                }
            }
        """)
    }

    private fun writePluginXmlFile() {
        pluginXml.xml("""
            <idea-plugin>
                <name>MyName</name>
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>JetBrains</vendor>
                <depends>com.intellij.modules.platform</depends>
            </idea-plugin>
        """)
    }
}
