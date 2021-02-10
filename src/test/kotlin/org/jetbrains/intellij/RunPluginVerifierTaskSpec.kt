package org.jetbrains.intellij

import org.apache.commons.io.FileUtils
import org.gradle.internal.impldep.com.google.gson.JsonParser
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunPluginVerifierTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `run plugin verifier in specified version`() {
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = "2020.2.3"
                verifierVersion = "1.241"
            }
        """)

        val result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        assertTrue(result.output.contains("Starting the IntelliJ Plugin Verifier 1.241"))
    }

    @Test
    fun `run plugin verifier in the latest version`() {
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"

            runPluginVerifier {
                ideVersions = "2020.2.3"
            }
        """)

        val result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)
        val version = requestPluginVerifierVersion()
        assertTrue(result.output.contains("Starting the IntelliJ Plugin Verifier $version"))
    }

    @Test
    fun `test plugin against two IDEs`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = "IC-2020.2.3,PS-2020.1.3"
            }
        """)

        val result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        assertTrue(result.output.contains("Plugin PluginName:1.0.0 against IC-202.7660.26: Compatible"))
        assertTrue(result.output.contains("Plugin PluginName:1.0.0 against PS-201.8538.41: Compatible"))
    }

    @Test
    fun `set verification reports directory`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"
            
            runPluginVerifier {
                verificationReportsDirectory = "${'$'}{project.buildDir}/foo"
                ideVersions = ["IC-2020.2.3"]
            }
        """)

        val result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        val directory = file("build/foo").canonicalPath
        assertTrue(result.output.contains("Verification reports directory: $directory"))
    }

    @Test
    fun `fail on missing ideVersions property`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"
        """)

        val result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        assertTrue(result.output.contains("`ideVersions` and `localPaths` properties should not be empty"))
    }

    @Test
    fun `fail on verifyPlugin task`() {
        writeJavaFile()
        pluginXml.delete()
        buildFile.groovy("""
            version = "1.0.0"
        """)

        val result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        assertTrue(result.output.contains("Plugin descriptor 'plugin.xml' is not found"))
        assertTrue(result.output.contains("Task :verifyPlugin FAILED"))
    }

    @Test
    fun `fail on Deprecated API usages`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.groovy("""
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                failureLevel = FailureLevel.DEPRECATED_API_USAGES
                ideVersions = "2020.2.3"
            }
        """)

        val result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        assertTrue(result.output.contains("Deprecated API usages"))
        assertTrue(result.output.contains("org.gradle.api.GradleException: DEPRECATED_API_USAGES"))
    }

    @Test
    fun `pass on Deprecated API usages`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.groovy("""
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = "2020.2.3"
            }
        """)

        val result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        assertTrue(result.output.contains("Deprecated API usages"))
        assertFalse(result.output.contains("org.gradle.api.GradleException: DEPRECATED_API_USAGES"))
    }

    @Test
    fun `fail on incorrect ideVersion`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.groovy("""
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = "foo,foo,,foo"
            }
        """)

        val result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        assertTrue(result.output.contains("IDE 'foo' cannot be downloaded."))
    }

    @Test
    fun `fail on any failureLevel`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.groovy("""
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = "2020.2.3"
                failureLevel = FailureLevel.ALL
            }
        """)

        val result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        assertTrue(result.output.contains("Deprecated API usages"))
        assertTrue(result.output.contains("org.gradle.api.GradleException: DEPRECATED_API_USAGES"))
    }

    @Test
    fun `pass on any failureLevel`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.groovy("""
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = "2020.2.3"
                failureLevel = FailureLevel.NONE
            }
        """)

        val result = build(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME)

        assertTrue(result.output.contains("Deprecated API usages"))
        assertFalse(result.output.contains("org.gradle.api.GradleException: DEPRECATED_API_USAGES"))
    }

    @Test
    fun `run plugin verifier in offline mode`() {
        writePluginXmlFile()
        warmupGradle()
        buildFile.groovy("""
            version = "1.0.0"

            runPluginVerifier {
                ideVersions = "2020.1.3"
                verifierPath = "${'$'}{project.buildDir}/pluginVerifier.jar"
            }
        """)

        val version = requestPluginVerifierVersion()
        FileUtils.copyInputStreamToFile(
            URL("https://dl.bintray.com/jetbrains/intellij-plugin-service/org/jetbrains/intellij/plugins/verifier-cli/$version/verifier-cli-$version-all.jar").openStream(),
            file("build/pluginVerifier.jar")
        )

        val result = buildAndFail(IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME, "--offline")
        assertTrue(result.output.contains("Gradle runs in offline mode."))
    }

    private fun warmupGradle() {
        buildFile.groovy("""
            version = "1.0.0"

            runPluginVerifier {
                ideVersions = "2020.2.3"
            }
        """)
        build(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)
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
                <name>PluginName</name>
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>JetBrains</vendor>
            </idea-plugin>
        """)
    }

    private fun requestPluginVerifierVersion(): String {
        val url = URL("https://api.bintray.com/packages/jetbrains/intellij-plugin-service/intellij-plugin-verifier/versions/_latest")
        val request = url.openConnection()
        request.connect()

        val jp = JsonParser() //from gson
        val root = jp.parse(InputStreamReader(request.content as InputStream)) //Convert the input stream to a json element
        val rootobj = root.asJsonObject //May be an array, may be an object.
        return rootobj.get("name").asString //just grab the zipcode
    }
}
