// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.resolveLatestVersion
import java.util.*
import kotlin.io.path.*
import kotlin.test.*

// todo must use test-local PV directory for storing downloaded IDEs instead of default one (machine)
class VerifyPluginTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `warn about no IDE picked for verification`() {
        writePluginVerifierDependency()

        buildAndFail(Tasks.VERIFY_PLUGIN) {
            assertContains("No IDE resolved for verification with the IntelliJ Plugin Verifier.", output)
        }
    }

    @Test
    fun `run plugin verifier in specified version`() {
        writePluginXmlFile()
        writePluginVerifierDependency("1.307")
        writePluginVerifierIde()

        build(Tasks.VERIFY_PLUGIN) {
            assertContains("Starting the IntelliJ Plugin Verifier 1.307", output)
        }
    }

    @Test
    fun `run plugin verifier fails on old version lower than 1_255`() {
        writePluginXmlFile()
        writePluginVerifierDependency("1.254")
        writePluginVerifierIde()

        build(
            gradleVersion = gradleVersion,
            fail = true,
            assertValidConfigurationCache = false,
            Tasks.VERIFY_PLUGIN,
        ) {
            assertContains("No IntelliJ Plugin Verifier executable found.", output)
        }
    }


    @Test
    fun `run plugin verifier and fail on invalid CLI path`() {
        writePluginXmlFile()
        writePluginVerifierIde()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        cliPath = file("invalid")
                    }
                }
                """.trimIndent()

        build(
            gradleVersion = gradleVersion,
            fail = true,
            assertValidConfigurationCache = false,
            Tasks.VERIFY_PLUGIN,
        ) {
            assertContains("IntelliJ Plugin Verifier not found at:", output)
            assertContains("No IntelliJ Plugin Verifier executable found.", output)
        }
    }

    @Test
    fun `run plugin verifier in the latest version`() {
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        build(Tasks.VERIFY_PLUGIN) {
            val latestVersion = Coordinates("org.jetbrains.intellij.plugins", "verifier-cli").resolveLatestVersion()

            assertContains("Starting the IntelliJ Plugin Verifier $latestVersion", output)
        }
    }

    @Test
    @Ignore
    fun `test plugin against two IDEs`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()
        writePluginVerifierIde("PS", "2022.3")

        build(Tasks.VERIFY_PLUGIN) {
            assertContains("Plugin MyName:1.0.0 against IC-223.8836.41: Compatible", output)
            assertContains("Plugin MyName:1.0.0 against PS-223.7571.212: Compatible", output)
        }
    }

    @Test
    @Ignore
    fun `test plugin against Android Studio`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde("AI", "2022.3.1.18")

        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        binaryReleasesAndroidStudio()
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN) {
            assertContains("Plugin MyName:1.0.0 against AI-223.8836.35.2231.10406996: Compatible", output)
        }
    }

    @Test
    fun `set verification reports directory`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        verificationReportsDirectory = project.layout.buildDirectory.dir("foo")
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN) {
            val directory = buildDirectory.resolve("foo")
            assertContains("Verification reports directory: ${directory.toRealPath()}", output)
        }
    }

    @Test
    fun `set verification reports output formats`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        verificationReportsDirectory = project.layout.buildDirectory.dir("foo")
                        verificationReportsFormats = listOf(VerificationReportsFormats.MARKDOWN, VerificationReportsFormats.PLAIN)
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN) {
            val reportsDirectory = buildDirectory.resolve("foo")
            assertContains("Verification reports directory: ${reportsDirectory.toRealPath()}", output)

            val ideVersionDir = reportsDirectory.listDirectoryEntries().firstOrNull()
            assertNotNull(ideVersionDir, "Verification reports directory not found")

            val markdownReportFiles = ideVersionDir.listDirectoryEntries("*.md")
            assertEquals(1, markdownReportFiles.size)
        }
    }

    @Test
    fun `set verification reports with empty set of output formats`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        verificationReportsFormats.empty()
                        verificationReportsDirectory = project.layout.buildDirectory.dir("foo")
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN) {
            val reportsDirectory = buildDirectory.resolve("foo")
            assertContains("Verification reports directory: ${reportsDirectory.toRealPath()}", output)

            val ideVersionDir = reportsDirectory.listDirectoryEntries().firstOrNull()
            assertNotNull(ideVersionDir, "Verification reports directory not found")

            val reportFiles = ideVersionDir.listDirectoryEntries("*.{md,html}")
            assertTrue(reportFiles.isEmpty())
        }
    }

    @Test
    fun `set verification reports with default settings`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        verificationReportsDirectory = project.layout.buildDirectory.dir("foo")
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN) {
            val reportsDirectory = buildDirectory.resolve("foo")
            assertContains("Verification reports directory: ${reportsDirectory.toRealPath()}", output)

            val ideVersionDir = reportsDirectory.listDirectoryEntries().firstOrNull()
            assertNotNull(ideVersionDir, "Verification reports directory not found")

            val reportFiles = ideVersionDir.listDirectoryEntries("*.{md,html}")
            assertTrue(reportFiles.isNotEmpty())
        }
    }

    @Test
    fun `set ignored problems file`() {
        writeJavaFileWithPluginProblems(classNameSuffix = UUID.randomUUID().toString().replace("-", ""))
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        val lines = listOf("MyName:1.0.0:Reference to a missing property.*")
        val ignoredProblems = createTempFile("ignored-problems", ".txt").writeLines(lines)

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        ignoredProblemsFile = file("${ignoredProblems.invariantSeparatorsPathString}")
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN) {
            assertContains(
                "Compatible. 1 usage of scheduled for removal API and 1 usage of deprecated API. 1 usage of internal API",
                output
            )
            assertNotContains("Reference to a missing property", output)
        }
    }

    @Test
    fun `fail on verifyPlugin task`() {
        writeJavaFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        pluginXml.deleteIfExists()

        buildAndFail(Tasks.VERIFY_PLUGIN) {
            assertContains("The plugin descriptor 'plugin.xml' is not found.", output)
        }
    }

    @Test
    fun `fail on Deprecated API usages`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        failureLevel = listOf(FailureLevel.DEPRECATED_API_USAGES)
                    }
                }
                """.trimIndent()

        buildAndFail(Tasks.VERIFY_PLUGIN) {
            assertContains("Deprecated API usages", output)
            assertContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", output)
        }
    }

    @Test
    fun `pass on Deprecated API usages`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        build(Tasks.VERIFY_PLUGIN) {
            assertContains("Deprecated API usages", output)
            assertNotContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", output)
        }
    }

    @Test
    fun `fail on incorrect ide version`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        ides {
                            ide("foo")
                        }
                    }
                }
                """.trimIndent()

        buildAndFail(Tasks.VERIFY_PLUGIN) {
            assertContains("Could not find", output)
            assertContains("idea:ideaIC:foo", output)
        }
    }

    @Ignore("Since we drop the until-build, the recommended() list grows fast and we can't run it on CI")
    @Test
    fun `pass on recommended ides`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        ides {
                            recommended()
                        }
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN) {
            val message = "Reading IDE "
            val line = output.lines().find { it.contains(message) }
            assertNotNull(line)

            val path = Path(line.substringAfter(message))
            assertExists(path)
//            assertEquals("IC-223.8836.26", path.name)
        }
    }

    @Test
    fun `fail on any failureLevel`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        failureLevel = FailureLevel.ALL
                    }
                }
                """.trimIndent()

        buildAndFail(Tasks.VERIFY_PLUGIN) {
            assertContains("Deprecated API usages", output)
            assertContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", output)
        }
    }

    @Test
    fun `pass on any failureLevel`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        failureLevel = VerifyPluginTask.FailureLevel.NONE
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN) {
            assertContains("Deprecated API usages", output)
            assertNotContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", output)
        }
    }

    @Test
    @Ignore
    fun `run plugin verifier in offline mode`() {
        writePluginXmlFile()
        writePluginVerifierIde()
        build(Tasks.BUILD_PLUGIN)

        writePluginVerifierDependency()
        writePluginVerifierIde(version = "9999.88.7") // version that will never exist in cache

        buildAndFail(Tasks.VERIFY_PLUGIN, "--offline") {
            assertContains("Could not resolve idea:ideaIC:9999.88.7", output)
            assertContains("No cached version of idea:ideaIC:9999.88.7 available for offline mode.", output)
        }
    }

    @Test
    @Ignore
    fun `pass on CLI arguments passed as free args`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        verificationReportsDirectory = project.layout.buildDirectory.dir("foo")
                        freeArgs = listOf("-verification-reports-formats", "plain") 
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN) {
            val reportsDirectory = buildDirectory.resolve("foo")
            assertContains("Verification reports directory: ${reportsDirectory.toRealPath()}", output)

            val ideVersionDir = reportsDirectory.listDirectoryEntries().firstOrNull()
            assertNotNull(ideVersionDir, "Verification reports directory not found")

            val reportFiles = ideVersionDir.listDirectoryEntries("*.{md,html}")
            assertTrue(reportFiles.isEmpty())
        }
    }

    @Test
    fun `pass on CLI arguments the internal API usage mode as a free arg`() {
        writeJavaFileWithInternalApiUsage()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        freeArgs = listOf("-suppress-internal-api-usages", "jetbrains-plugins") 
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN) {
            assertNotContains("Internal API usages (2):", output)
        }
    }

    private fun writePluginVerifierDependency(version: String? = null) {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        pluginVerifier(${version?.let { "\"$it\"" }.orEmpty()})
                    }
                }
                """.trimIndent()
    }

    private fun writePluginVerifierIde(type: String = intellijPlatformType, version: String = intellijPlatformVersion) {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        ides {
                            ide("$type", "$version")
                        }
                    }
                }
                """.trimIndent()
    }

    private fun writeJavaFileWithDeprecation() {
        dir.resolve("src/main/java/App.java") write //language=java
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
    }

    private fun writeJavaFileWithPluginProblems(classNameSuffix: String) {
        @Suppress("UnresolvedPropertyKey", "ResultOfMethodCallIgnored")
        dir.resolve("src/main/java/App$classNameSuffix.java") write //language=java
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
    }

    private fun writeJavaFileWithInternalApiUsage() {
        dir.resolve("src/main/java/App.java") write //language=java
                """
                class App {
                    public static void main(String[] args) {
                        new com.intellij.DynamicBundle.LanguageBundleEP();
                    }
                }
                """.trimIndent()
    }

    private fun writePluginXmlFile() {
        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>MyName</name>
                    <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                    <vendor>JetBrains</vendor>
                    <depends>com.intellij.modules.platform</depends>
                </idea-plugin>
                """.trimIndent()
    }
}
