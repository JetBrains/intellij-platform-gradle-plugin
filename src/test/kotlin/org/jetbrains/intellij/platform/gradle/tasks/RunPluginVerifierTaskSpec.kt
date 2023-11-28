// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.kotlin.dsl.support.listFilesOrdered
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import org.jetbrains.intellij.platform.gradle.utils.LatestVersionResolver
import java.util.*
import kotlin.io.path.createTempFile
import kotlin.io.path.pathString
import kotlin.io.path.writeLines
import kotlin.test.*

class RunPluginVerifierTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `warn about no IDE picked for verification`() {
        writePluginVerifierDependency()

        buildAndFail(Tasks.RUN_PLUGIN_VERIFIER) {
            assertContains("No IDE selected for verification with the IntelliJ Plugin Verifier", output)
        }
    }

    @Test
    fun `warn about too low IDE version`() {
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde(version = "2020.2.3")

        buildAndFail(Tasks.RUN_PLUGIN_VERIFIER) {
            assertContains("The minimal supported IDE version is 223+, the provided version is too low: 2020.2.3 (202.7660.26)", output)
        }
    }

    @Test
    fun `run plugin verifier in specified version`() {
        writePluginXmlFile()
        writePluginVerifierDependency("1.304")
        writePluginVerifierIde()

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            assertContains("Starting the IntelliJ Plugin Verifier 1.304", output)
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
            Tasks.RUN_PLUGIN_VERIFIER,
        ) {
            assertContains("Could not find org.jetbrains.intellij.plugins:verifier-cli:1.254", output)
        }
    }

    @Test
    fun `run plugin verifier in the latest version`() {
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            val version = LatestVersionResolver.pluginVerifier()
            assertContains("Starting the IntelliJ Plugin Verifier $version", output)
        }
    }

    @Test
    fun `test plugin against two IDEs`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()
        writePluginVerifierIde("PS", "2022.3")

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            assertContains("Plugin projectName:1.0.0 against IC-223.8836.14: Compatible", output)
            assertContains("Plugin projectName:1.0.0 against PS-223.7571.212: Compatible", output)
        }
    }

    @Test
    fun `test plugin against Android Studio`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde("AI", "2022.3.1.18")

        buildFile.kotlin(
            """
            repositories {
                intellijPlatform {
                    binaryReleasesAndroidStudio()
                }
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            assertContains("Plugin projectName:1.0.0 against AI-223.8836.35.2231.10406996: Compatible", output)
        }
    }

    @Test
    fun `set verification reports directory`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile.kotlin(
            """
            intellijPlatform {
                pluginVerifier {
                    verificationReportsDirectory = project.layout.buildDirectory.dir("foo")
                }
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            val directory = file("build/foo").canonicalPath
            assertContains("Verification reports directory: $directory", output)
        }
    }

    @Test
    fun `set verification reports output formats`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile.kotlin(
            """
            intellijPlatform {
                pluginVerifier {
                    verificationReportsDirectory = project.layout.buildDirectory.dir("foo")
                    verificationReportsFormats = listOf(VerificationReportsFormats.MARKDOWN, VerificationReportsFormats.PLAIN)
                }
            }
            """.trimIndent()
        )

        println("buildFile = ${buildFile}")

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            val reportsDirectory = file("build/foo")
            val directory = reportsDirectory.canonicalPath
            assertContains("Verification reports directory: $directory", output)

            val ideVersionDir = reportsDirectory.listFiles()?.firstOrNull()
            assertNotNull(ideVersionDir, "Verification reports directory not found")

            val markdownReportFiles = ideVersionDir.listFilesOrdered { it.extension == "md" }
            assertEquals(1, markdownReportFiles.size)
        }
    }

    @Test
    fun `set verification reports with empty set of output formats`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile.kotlin(
            """
            intellijPlatform {
                pluginVerifier {
                    verificationReportsFormats.empty()
                    verificationReportsDirectory = project.layout.buildDirectory.dir("foo")
                }
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            val reportsDirectory = file("build/foo")
            val directory = reportsDirectory.canonicalPath
            assertContains("Verification reports directory: $directory", output)

            val ideVersionDir = reportsDirectory.listFiles()?.firstOrNull()
            assertNotNull(ideVersionDir, "Verification reports directory not found")

            val reportFiles = ideVersionDir.listFilesOrdered { listOf("md", "html").contains(it.extension) }
            assertTrue(reportFiles.isEmpty())
        }
    }

    @Test
    fun `set verification reports with default settings`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile.kotlin(
            """
            intellijPlatform {
                pluginVerifier {
                    verificationReportsDirectory = project.layout.buildDirectory.dir("foo")
                }
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            val reportsDirectory = file("build/foo")
            val directory = reportsDirectory.canonicalPath
            assertContains("Verification reports directory: $directory", output)

            val ideVersionDir = reportsDirectory.listFiles()?.firstOrNull()
            assertNotNull(ideVersionDir, "Verification reports directory not found")

            val reportFiles = ideVersionDir.listFilesOrdered { listOf("md", "html").contains(it.extension) }
            assertTrue(reportFiles.isNotEmpty())
        }
    }

    @Test
    fun `set ignored problems file`() {
        writeJavaFileWithPluginProblems(classNameSuffix = UUID.randomUUID().toString().replace("-", ""))
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        val lines = listOf("projectName:1.0.0:Reference to a missing property.*")
        val ignoredProblems = createTempFile("ignored-problems", ".txt").writeLines(lines)
        val ignoredProblemsFilePath = adjustWindowsPath(ignoredProblems.pathString)

        buildFile.kotlin(
            """
            intellijPlatform {
                pluginVerifier {
                    ignoredProblemsFile = file("$ignoredProblemsFilePath")
                }
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            assertContains("Compatible. 1 usage of scheduled for removal API and 1 usage of deprecated API. 1 usage of internal API", output)
            assertNotContains("Reference to a missing property", output)
        }
    }

    @Test
    fun `use ListProductsReleasesTask output on missing ideVersions property`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        val resource = resource("products-releases/idea-releases.xml")
        buildFile.kotlin(
            """
            tasks {
                listProductsReleases {
                    ideaProductReleasesUpdateFiles.from("$resource")
                    sinceVersion = "2020.2"
                    untilVersion = "2020.2.3"
                    releaseChannels = EnumSet.of(Channel.RELEASE)
                }
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            assertContains("> Task :listProductsReleases", output)
            assertContains("Starting the IntelliJ Plugin Verifier", output)
        }
    }

    @Test
    fun `fail on verifyPlugin task`() {
        writeJavaFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        pluginXml.delete()

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            assertContains("The plugin descriptor 'plugin.xml' is not found.", output)
        }
    }

    @Test
    fun `fail on Deprecated API usages`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile.kotlin(
            """
            intellijPlatform {
                pluginVerifier {
                    failureLevel = listOf(FailureLevel.DEPRECATED_API_USAGES)
                }
            }
            """.trimIndent()
        )

        buildAndFail(Tasks.RUN_PLUGIN_VERIFIER) {
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

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            assertContains("Deprecated API usages", output)
            assertNotContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", output)
        }
    }

    @Test
    fun `fail on incorrect ideVersion`() {
        writeJavaFile()
        writePluginXmlFile()
        writePluginVerifierDependency()

        buildFile.kotlin(
            """
            intellijPlatform {
                pluginVerifier {
                    ides {
                        ide("foo")
                    }
                }
            }
            """.trimIndent()
        )

        buildAndFail(Tasks.RUN_PLUGIN_VERIFIER) {
            assertContains("Could not find idea:ideaIC:foo.", output)
        }
    }

    @Test
    fun `fail on any failureLevel`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile.kotlin(
            """            
            intellijPlatform {
                pluginVerifier {
                    failureLevel = FailureLevel.ALL
                }
            }
            """.trimIndent()
        )

        buildAndFail(Tasks.RUN_PLUGIN_VERIFIER) {
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

        buildFile.kotlin(
            """
            tasks {
                runPluginVerifier {
                    failureLevel = RunPluginVerifierTask.FailureLevel.NONE
                }
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            assertContains("Deprecated API usages", output)
            assertNotContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", output)
        }
    }

    @Test
    fun `run plugin verifier in offline mode`() {
        writePluginXmlFile()
        writePluginVerifierIde()
        build(Tasks.BUILD_PLUGIN)

        writePluginVerifierDependency()
        writePluginVerifierIde(version = "2022.3.1")

        buildAndFail(Tasks.RUN_PLUGIN_VERIFIER, "--offline") {
            assertContains("Could not resolve idea:ideaIC:2022.3.1", output)
            assertContains("No cached version of idea:ideaIC:2022.3.1 available for offline mode.", output)
        }
    }

    @Test
    @Ignore
    fun `pass on CLI arguments passed as free args`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile.kotlin(
            """
            intellijPlatform {
                pluginVerifier {
                    verificationReportsDirectory = project.layout.buildDirectory.dir("foo")
                    freeArgs = listOf("-verification-reports-formats", "plain") 
                }
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            val reportsDirectory = file("build/foo")
            val directory = reportsDirectory.canonicalPath
            assertContains("Verification reports directory: $directory", output)

            val ideDirs = reportsDirectory.listFiles() ?: emptyArray()
            if (ideDirs.isEmpty()) {
                fail("Verification reports directory not found")
            }
            val ideVersionDir = ideDirs.first()
            val reportFiles = ideVersionDir.listFilesOrdered { listOf("md", "html").contains(it.extension) }
            assertTrue(reportFiles.isEmpty())
        }
    }

    @Test
    fun `pass on CLI arguments the internal API usage mode as a free arg`() {
        writeJavaFileWithInternalApiUsage()
        writePluginXmlFile()
        writePluginVerifierDependency()
        writePluginVerifierIde()

        buildFile.kotlin(
            """
            intellijPlatform {
                pluginVerifier {
                    freeArgs = listOf("-suppress-internal-api-usages", "jetbrains-plugins") 
                }
            }
            """.trimIndent()
        )

        build(Tasks.RUN_PLUGIN_VERIFIER) {
            assertNotContains("Internal API usages (2):", output)
        }
    }

    private fun writePluginVerifierDependency(version: String? = null) {
        buildFile.kotlin(
            """
            repositories {
                intellijPlatform {
                    binaryReleases()
                    pluginVerifier()
                }
            }
            dependencies {
                intellijPlatform {
                    pluginVerifier(${version?.let { "\"$it\"" }.orEmpty()})
                }
            }
            """.trimIndent()
        )
    }

    private fun writePluginVerifierIde(type: String = intellijType, version: String = intellijVersion) {
        buildFile.kotlin(
            """
            intellijPlatform {
                pluginVerifier {
                    ides {
                        ide("$type", "$version")
                    }
                }
            }
            """.trimIndent()
        )
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
