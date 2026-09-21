// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression test for [#2247](https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2247).
 *
 * Since 2.19.0 the module plugin requests `LibraryElements=composed-jar` per project dependency. Attaching an
 * attribute to a project dependency that pins an explicit target configuration (e.g.
 * `testImplementation(project(":core", "testOutput"))`, a common way of sharing test classes between modules)
 * made Gradle fail the build with "Cannot add attributes or capabilities on a dependency that specifies
 * artifacts or configuration information". The fix skips such dependencies, so the classpath resolves again.
 */
class SharedTestOutputConfigurationRegressionTest : IntelliJPluginTestBase() {

    @Test
    fun `project dependency pinning a custom target configuration does not break the module plugin`() {
        settingsFile overwrite //language=kotlin
                """
                rootProject.name = "projectName"

                include("core", "app")
                """.trimIndent()

        buildFile overwrite //language=kotlin
                """
                // Root project intentionally left without any plugin.
                """.trimIndent()

        // A `core` module that exposes its compiled test classes through a custom consumable `testOutput`
        // configuration, mimicking the reporter's setup for sharing test framework/utility classes.
        dir.resolve("core/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    `java-library`
                }

                val testOutput = configurations.create("testOutput") {
                    isCanBeConsumed = true
                    isCanBeResolved = false
                }

                dependencies {
                    testOutput(sourceSets.getByName("test").output.classesDirs)
                }
                """.trimIndent()

        dir.resolve("core/src/main/java/com/example/core/Core.java") write //language=java
                """
                package com.example.core;

                public class Core {
                }
                """.trimIndent()

        dir.resolve("core/src/test/java/com/example/core/CoreTestSupport.java") write //language=java
                """
                package com.example.core;

                public class CoreTestSupport {
                }
                """.trimIndent()

        // The plugin consumer. It depends on `core` normally and consumes the shared test classes through the
        // `testOutput` configuration, which is what used to fail during dependency resolution.
        dir.resolve("app/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    id("java")
                    id("org.jetbrains.intellij.platform.module")
                }

                dependencies {
                    implementation(project(":core"))
                    testImplementation(project(":core", "testOutput"))
                }

                val testCompileClasspathFiles = configurations["testCompileClasspath"].incoming.files
                tasks.register("printTestCompileClasspath") {
                    doLast {
                        testCompileClasspathFiles.forEach { println("TEST_COMPILE_CLASSPATH_ENTRY: " + it.name) }
                    }
                }
                """.trimIndent()

        // Test source referencing the shared `core` test class, so `:app:compileTestJava` only succeeds when
        // the shared test output survives on the test classpath.
        dir.resolve("app/src/test/java/com/example/app/AppTest.java") write //language=java
                """
                package com.example.app;

                import com.example.core.Core;
                import com.example.core.CoreTestSupport;

                public class AppTest {
                    Core core = new Core();
                    CoreTestSupport support = new CoreTestSupport();
                }
                """.trimIndent()

        build(":app:printTestCompileClasspath", ":app:compileTestJava") {
            assertTrue(
                output.contains("TEST_COMPILE_CLASSPATH_ENTRY:"),
                "Expected the test compile classpath to resolve without failing (#2247).",
            )
        }
    }
}
