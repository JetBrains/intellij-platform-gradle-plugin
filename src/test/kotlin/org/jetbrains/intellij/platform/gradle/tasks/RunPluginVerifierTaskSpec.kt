// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.apache.commons.io.FileUtils
import org.gradle.kotlin.dsl.support.listFilesOrdered
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Locations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import org.junit.Assert
import java.net.URL
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.io.path.writeLines
import kotlin.test.Ignore
import kotlin.test.Test

@Suppress("GroovyUnusedAssignment", "PluginXmlValidity", "ComplexRedundantLet")
class RunPluginVerifierTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `run plugin verifier in specified version`() {
        writePluginXmlFile()
        buildFile.kotlin(
            """
            repositories {
                intellijPlatform.pluginVerifier()
            }
            dependencies {
                intellijPlatform.pluginVerifier()
            }
            intellijPlatform {
                pluginVerifier {
                    ideVersions = ["2020.2.3"]
                }
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let {
            assertContains("Starting the IntelliJ Plugin Verifier 1.255", it.output)
        }
    }

    @Test
    fun `run plugin verifier fails on old version lower than 1_255`() {
        writePluginXmlFile()
        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
                verifierVersion = "1.254"
            }
            """.trimIndent()
        )

        build(
            gradleVersion = gradleVersion,
            fail = true,
            assertValidConfigurationCache = false,
            Tasks.RUN_PLUGIN_VERIFIER,
        ).let {
            assertContains("Could not find org.jetbrains.intellij.plugins:verifier-cli:1.254", it.output)
        }
    }

    @Test
    fun `run plugin verifier in the latest version`() {
        writePluginXmlFile()
        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let {
            val version = RunPluginVerifierTask.resolveLatestVersion()
            assertContains("Starting the IntelliJ Plugin Verifier $version", it.output)
        }
    }

    @Test
    fun `test plugin against two IDEs`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["IC-2020.2.3", "PS-2020.1.3"]
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let {
            assertContains("Plugin MyName:1.0.0 against IC-202.7660.26: Compatible", it.output)
            assertContains("Plugin MyName:1.0.0 against PS-201.8538.41: Compatible", it.output)
        }
    }

    @Test
    fun `test plugin against Android Studio`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["AI-2021.1.1.15"]
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let {
            assertContains("Plugin MyName:1.0.0 against AI-211.7628.21.2111.7824002: Compatible", it.output)
        }
    }

    @Test
    fun `set verification reports directory`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                verificationReportsDir = "${'$'}{project.buildDir}/foo"
                ideVersions = ["IC-2020.2.3"]
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let {
            val directory = file("build/foo").canonicalPath
            assertContains("Verification reports directory: $directory", it.output)
        }
    }

    @Test
    fun `set verification reports output formats`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.kotlin(
            """
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.VerificationReportsFormats
            
            version = "1.0.0"
            
            runPluginVerifier {
                verificationReportsFormats = [ VerificationReportsFormats.MARKDOWN, VerificationReportsFormats.PLAIN ]
                verificationReportsDir = "${'$'}{project.buildDir}/foo"
                ideVersions = ["IC-2020.2.3"]
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let { buildResult ->
            val reportsDirectory = file("build/foo")
            val directory = reportsDirectory.canonicalPath
            assertContains("Verification reports directory: $directory", buildResult.output)
            val ideDirs = reportsDirectory.listFiles()
            if (ideDirs.isEmpty()) {
                Assert.fail("Verification reports directory not found")
            }
            val ideVersionDir = ideDirs.first()
            val markdownReportFiles = ideVersionDir.listFilesOrdered { it.extension == "md" }
            Assert.assertEquals(1, markdownReportFiles.size)
        }
    }

    @Test
    fun `set verification reports with empty set of output formats`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                verificationReportsFormats = []
                verificationReportsDir = "${'$'}{project.buildDir}/foo"
                ideVersions = ["IC-2020.2.3"]
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let { buildResult ->
            val reportsDirectory = file("build/foo")
            val directory = reportsDirectory.canonicalPath
            assertContains("Verification reports directory: $directory", buildResult.output)
            val ideDirs = reportsDirectory.listFiles()
            if (ideDirs.isEmpty()) {
                Assert.fail("Verification reports directory not found")
            }
            val ideVersionDir = ideDirs.first()
            val reportFiles = ideVersionDir.listFilesOrdered { listOf("md", "html").contains(it.extension) }
            Assert.assertTrue(reportFiles.isEmpty())
        }
    }

    @Test
    fun `set verification reports with default settings`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                verificationReportsDir = "${'$'}{project.buildDir}/foo"
                ideVersions = ["IC-2020.2.3"]
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let { buildResult ->
            val reportsDirectory = file("build/foo")
            val directory = reportsDirectory.canonicalPath
            assertContains("Verification reports directory: $directory", buildResult.output)
            val ideDirs = reportsDirectory.listFiles()
            if (ideDirs.isEmpty()) {
                Assert.fail("Verification reports directory not found")
            }
            val ideVersionDir = ideDirs.first()
            val reportFiles = ideVersionDir.listFilesOrdered { listOf("md", "html").contains(it.extension) }
            Assert.assertTrue(reportFiles.isNotEmpty())
        }
    }

    @Test
    fun `set ignored problems file`() {
        writeJavaFileWithPluginProblems(classNameSuffix = UUID.randomUUID().toString().replace("-", ""))
        writePluginXmlFile()

        val lines = listOf("MyName:1.0.0:Reference to a missing property.*")
        val ignoredProblems = createTempFile("ignored-problems", ".txt")
            .writeLines(lines)
        val ignoredProblemsFilePath = adjustWindowsPath(ignoredProblems.absolutePathString())

        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ignoredProblems = file("$ignoredProblemsFilePath")
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let {
            assertContains("Compatible. 1 usage of scheduled for removal API and 1 usage of deprecated API. 1 usage of internal API", it.output)
            assertNotContains("Reference to a missing property", it.output)
        }
    }

    @Test
    fun `use ListProductsReleasesTask output on missing ideVersions property`() {
        writeJavaFile()
        writePluginXmlFile()

        val resource = resolveResourcePath("products-releases/idea-releases.xml")
        buildFile.kotlin(
            """
            import org.jetbrains.intellij.platform.gradle.tasks.ListProductsReleasesTask.Channel
            
            version = "1.0.0"
            
            listProductsReleases {
                ideaProductReleasesUpdateFiles.setFrom(['${resource}'])
                sinceVersion = "2020.2"
                untilVersion = "2020.2.3"
                releaseChannels = EnumSet.of(Channel.RELEASE)
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let {
            assertContains("> Task :listProductsReleases", it.output)
            assertContains("Starting the IntelliJ Plugin Verifier", it.output)
        }
    }

    @Test
    fun `do not use ListProductsReleasesTask output on empty array passed to ideVersions property`() {
        writeJavaFile()
        writePluginXmlFile()

        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = []
                localPaths = [new File('/tmp')]
            }
            """.trimIndent()
        )

        buildAndFail(Tasks.RUN_PLUGIN_VERIFIER).let {
            assertContains("> Task :listProductsReleases SKIPPED", it.output)
        }
    }

    @Test
    fun `fail on verifyPlugin task`() {
        writeJavaFile()
        pluginXml.delete()
        buildFile.kotlin(
            """
            version = "1.0.0"
            """.trimIndent()
        )

        buildAndFail(Tasks.RUN_PLUGIN_VERIFIER).let {
            assertContains("Plugin descriptor 'plugin.xml' is not found", it.output)
            assertContains("Task :verifyPlugin FAILED", it.output)
        }
    }

    @Test
    fun `fail on Deprecated API usages`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.kotlin(
            """
            import org.jetbrains.intellij.platform.gradle.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                failureLevel = [FailureLevel.DEPRECATED_API_USAGES]
                ideVersions = ["2020.2.3"]
            }
            """.trimIndent()
        )

        buildAndFail(Tasks.RUN_PLUGIN_VERIFIER).let {
            assertContains("Deprecated API usages", it.output)
            assertContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", it.output)
        }
    }

    @Test
    fun `pass on Deprecated API usages`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let {
            assertContains("Deprecated API usages", it.output)
            assertNotContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", it.output)
        }
    }

    @Test
    fun `fail on incorrect ideVersion`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.kotlin(
            """
                                    version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["foo", "foo", "", "foo"]
            }
            """.trimIndent()
        )

        buildAndFail(Tasks.RUN_PLUGIN_VERIFIER).let {
            assertContains("IDE 'foo' cannot be downloaded.", it.output)
        }
    }

    @Test
    fun `fail on any failureLevel`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.kotlin(
            """
            import org.jetbrains.intellij.platform.gradle.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
                failureLevel = FailureLevel.ALL
            }
            """.trimIndent()
        )

        buildAndFail(Tasks.RUN_PLUGIN_VERIFIER).let {
            assertContains("Deprecated API usages", it.output)
            assertContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", it.output)
        }
    }

    @Test
    fun `pass on any failureLevel`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.kotlin(
            """
            tasks {
                runPluginVerifier {
                    ideVersions = listOf("2020.2.3")
                    failureLevel = RunPluginVerifierTask.FailureLevel.NONE
                }
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let {
            assertContains("Deprecated API usages", it.output)
            assertNotContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", it.output)
        }
    }

    @Test
    fun `run plugin verifier in offline mode`() {
        writePluginXmlFile()
        warmupGradle()
        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.1.3"]
                verifierPath = "${'$'}{project.buildDir}/pluginVerifier.jar"
            }
            """.trimIndent()
        )

        val version = RunPluginVerifierTask.resolveLatestVersion()
        URL("${Locations.PLUGIN_VERIFIER_REPOSITORY}/org/jetbrains/intellij/plugins/verifier-cli/$version/verifier-cli-$version-all.jar")
            .openStream().use {
                FileUtils.copyInputStreamToFile(it, file("build/pluginVerifier.jar"))
            }

        buildAndFail(Tasks.RUN_PLUGIN_VERIFIER, "--offline").let {
            assertContains("Cannot download", it.output)
        }
    }

    @Test
    @Ignore
    fun `pass on CLI arguments passed as free args`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2022.2.3"]
                verificationReportsDir = "${'$'}{project.buildDir}/foo"                
                freeArgs = [ "-verification-reports-formats", "plain" ] 
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let { buildResult ->
            val reportsDirectory = file("build/foo")
            val directory = reportsDirectory.canonicalPath
            assertContains("Verification reports directory: $directory", buildResult.output)
            val ideDirs = reportsDirectory.listFiles() ?: emptyArray()
            if (ideDirs.isEmpty()) {
                Assert.fail("Verification reports directory not found")
            }
            val ideVersionDir = ideDirs.first()
            val reportFiles = ideVersionDir.listFilesOrdered { listOf("md", "html").contains(it.extension) }
            Assert.assertTrue(reportFiles.isEmpty())
        }
    }

    @Test
    fun `pass on CLI arguments the internal API usage mode as a free arg`() {
        writeJavaFileWithInternalApiUsage()
        writePluginXmlFile()
        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2023.1"]
                freeArgs = [ "-suppress-internal-api-usages", "jetbrains-plugins" ] 
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER).let { buildResult ->
            assertNotContains("Internal API usages (2):", buildResult.output)
        }
    }

    private fun warmupGradle() {
        buildFile.kotlin(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
            }
            """.trimIndent()
        )
        build(Tasks.BUILD_PLUGIN)
    }

    private fun writeJavaFileWithDeprecation() {
        file("src/main/java/App.java").java(
            """  
            import java.lang.String;
            import org.jetbrains.annotations.NotNull;
            import com.intellij.openapi.util.text.StringUtil;
            
            class App {
            
                public static void main(@NotNull String[] strings) {
                    StringUtil.escapeXml("<foo>");
                }
            }
            """.trimIndent()
        )
    }

    private fun writeJavaFileWithPluginProblems(classNameSuffix: String) {
        @Suppress("UnresolvedPropertyKey", "ResultOfMethodCallIgnored")
        file("src/main/java/App$classNameSuffix.java").java(
            """  
            class App$classNameSuffix {
                public static String message(@org.jetbrains.annotations.PropertyKey(resourceBundle = "messages.ActionsBundle") String key, Object... params) {
                    return null;
                }
            
                public static void main(String[] args) {
                    App$classNameSuffix.message("somemessage", "someparam1");
                
                    System.out.println(com.intellij.openapi.project.ProjectCoreUtil.theProject);
                    
                    com.intellij.openapi.project.ProjectCoreUtil util = new com.intellij.openapi.project.ProjectCoreUtil();
                    System.out.println(util.theProject);
                    
                    System.out.println(com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER);
                    com.intellij.openapi.components.ServiceManager.getService(String.class);
                }
            }
            """.trimIndent()
        )
    }

    private fun writeJavaFileWithInternalApiUsage() {
        file("src/main/java/App.java").java(
            """  
            class App {
                public static void main(String[] args) {
                    new com.intellij.DynamicBundle.LanguageBundleEP();
                }
            }
            """.trimIndent()
        )
    }

    private fun writePluginXmlFile() {
        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyName</name>
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>JetBrains</vendor>
                <depends>com.intellij.modules.platform</depends>
            </idea-plugin>
            """.trimIndent()
        )
    }
}
