// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.apache.commons.io.FileUtils
import org.jetbrains.intellij.IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_VERIFIER_REPOSITORY
import org.jetbrains.intellij.IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.net.URL
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.io.path.writeLines
import kotlin.test.Test

@Suppress("GroovyUnusedAssignment", "PluginXmlValidity", "ComplexRedundantLet")
class RunPluginVerifierTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `run plugin verifier in specified version`() {
        writePluginXmlFile()
        buildFile.groovy(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
                verifierVersion = "1.255"
            }
            """.trimIndent()
        )

        build(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            assertContains("Starting the IntelliJ Plugin Verifier 1.255", it.output)
        }
    }

    @Test
    fun `run plugin verifier fails on old version lower than 1_255`() {
        writePluginXmlFile()
        buildFile.groovy(
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
            RUN_PLUGIN_VERIFIER_TASK_NAME,
        ).let {
            assertContains("Could not find org.jetbrains.intellij.plugins:verifier-cli:1.254", it.output)
        }
    }

    @Test
    fun `run plugin verifier in the latest version`() {
        writePluginXmlFile()
        buildFile.groovy(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
            }
            """.trimIndent()
        )

        build(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            val version = RunPluginVerifierTask.resolveLatestVersion()
            assertContains("Starting the IntelliJ Plugin Verifier $version", it.output)
        }
    }

    @Test
    fun `test plugin against two IDEs`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.groovy(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["IC-2020.2.3", "PS-2020.1.3"]
            }
            """.trimIndent()
        )

        build(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            assertContains("Plugin MyName:1.0.0 against IC-202.7660.26: Compatible", it.output)
            assertContains("Plugin MyName:1.0.0 against PS-201.8538.41: Compatible", it.output)
        }
    }

    @Test
    fun `test plugin against Android Studio`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.groovy(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["AI-2021.1.1.15"]
            }
            """.trimIndent()
        )

        build(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            assertContains("Plugin MyName:1.0.0 against AI-211.7628.21.2111.7824002: Compatible", it.output)
        }
    }

    @Test
    fun `set verification reports directory`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.groovy(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                verificationReportsDir = "${'$'}{project.buildDir}/foo"
                ideVersions = ["IC-2020.2.3"]
            }
            """.trimIndent()
        )

        build(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            val directory = file("build/foo").canonicalPath
            assertContains("Verification reports directory: $directory", it.output)
        }
    }

    @Test
    fun `set ignored problems file`() {
        writeJavaFileWithPluginProblems(classNameSuffix = UUID.randomUUID().toString().replace("-", ""))
        writePluginXmlFile()

        val lines = listOf("MyName:1.0.0:Reference to a missing property.*")
        val ignoredProblems = createTempFile("ignored-problems", ".txt")
            .writeLines(lines)
        val ignoredProblemsFilePath = ignoredProblems.absolutePathString()

        buildFile.groovy(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ignoredProblems = file("$ignoredProblemsFilePath")
            }
            """.trimIndent()
        )

        build(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            assertContains("Compatible. 1 usage of scheduled for removal API and 1 usage of deprecated API. 1 usage of internal API", it.output)
            assertNotContains("Reference to a missing property", it.output)
        }
    }

    @Test
    fun `use ListProductsReleasesTask output on missing ideVersions property`() {
        writeJavaFile()
        writePluginXmlFile()

        val resource = resolveResourcePath("products-releases/idea-releases.xml")
        buildFile.groovy(
            """
            import org.jetbrains.intellij.tasks.ListProductsReleasesTask.Channel
            
            version = "1.0.0"
            
            listProductsReleases {
                ideaProductReleasesUpdateFiles.setFrom(['${resource}'])
                sinceVersion = "2020.2"
                untilVersion = "2020.2.3"
                releaseChannels = EnumSet.of(Channel.RELEASE)
            }
            """.trimIndent()
        )

        build(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            assertContains("> Task :listProductsReleases", it.output)
            assertContains("Starting the IntelliJ Plugin Verifier", it.output)
        }
    }

    @Test
    fun `do not use ListProductsReleasesTask output on empty array passed to ideVersions property`() {
        writeJavaFile()
        writePluginXmlFile()

        buildFile.groovy(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = []
                localPaths = [new File('/tmp')]
            }
            """.trimIndent()
        )

        buildAndFail(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            assertContains("> Task :listProductsReleases SKIPPED", it.output)
        }
    }

    @Test
    fun `fail on verifyPlugin task`() {
        writeJavaFile()
        pluginXml.delete()
        buildFile.groovy(
            """
            version = "1.0.0"
            """.trimIndent()
        )

        buildAndFail(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            assertContains("Plugin descriptor 'plugin.xml' is not found", it.output)
            assertContains("Task :verifyPlugin FAILED", it.output)
        }
    }

    @Test
    fun `fail on Deprecated API usages`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.groovy(
            """
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                failureLevel = [FailureLevel.DEPRECATED_API_USAGES]
                ideVersions = ["2020.2.3"]
            }
            """.trimIndent()
        )

        buildAndFail(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            assertContains("Deprecated API usages", it.output)
            assertContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", it.output)
        }
    }

    @Test
    fun `pass on Deprecated API usages`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.groovy(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
            }
            """.trimIndent()
        )

        build(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            assertContains("Deprecated API usages", it.output)
            assertNotContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", it.output)
        }
    }

    @Test
    fun `fail on incorrect ideVersion`() {
        writeJavaFile()
        writePluginXmlFile()
        buildFile.groovy(
            """
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["foo", "foo", "", "foo"]
            }
            """.trimIndent()
        )

        buildAndFail(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            assertContains("IDE 'foo' cannot be downloaded.", it.output)
        }
    }

    @Test
    fun `fail on any failureLevel`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.groovy(
            """
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
                failureLevel = FailureLevel.ALL
            }
            """.trimIndent()
        )

        buildAndFail(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            assertContains("Deprecated API usages", it.output)
            assertContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", it.output)
        }
    }

    @Test
    fun `pass on any failureLevel`() {
        writeJavaFileWithDeprecation()
        writePluginXmlFile()
        buildFile.groovy(
            """
            import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
            
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
                failureLevel = FailureLevel.NONE
            }
            """.trimIndent()
        )

        build(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            assertContains("Deprecated API usages", it.output)
            assertNotContains("org.gradle.api.GradleException: DEPRECATED_API_USAGES", it.output)
        }
    }

    @Test
    fun `run plugin verifier in offline mode`() {
        writePluginXmlFile()
        warmupGradle()
        buildFile.groovy(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.1.3"]
                verifierPath = "${'$'}{project.buildDir}/pluginVerifier.jar"
            }
            """.trimIndent()
        )

        val version = RunPluginVerifierTask.resolveLatestVersion()
        URL("$PLUGIN_VERIFIER_REPOSITORY/org/jetbrains/intellij/plugins/verifier-cli/$version/verifier-cli-$version-all.jar")
            .openStream().use {
                FileUtils.copyInputStreamToFile(it, file("build/pluginVerifier.jar"))
            }

        buildAndFail(RUN_PLUGIN_VERIFIER_TASK_NAME, "--offline").let {
            assertContains("Cannot download", it.output)
        }
    }

    private fun warmupGradle() {
        buildFile.groovy(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
            }
            """.trimIndent()
        )
        build(BUILD_PLUGIN_TASK_NAME)
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
