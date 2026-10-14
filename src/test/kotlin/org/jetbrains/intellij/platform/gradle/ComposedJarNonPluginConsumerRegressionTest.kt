// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression test for projects that don't apply the IntelliJ Platform plugin but consume a module that does.
 *
 * Since 2.19.0 the module plugin requests `LibraryElements=composed-jar` per project dependency declared on the
 * dependency buckets (`api`, `implementation`, …). Those buckets also back the consumable variants
 * (`apiElements`, `runtimeElements`), so the request travels with the module's variant metadata to its consumers.
 * When a consumer without the plugin resolves such a module, it also resolves the module's project dependencies
 * with the `composed-jar` request, but its attributes schema lacks [ComposedJarRule][org.jetbrains.intellij.platform.gradle.attributes.ComposedJarRule].
 * A plain `jar` from a non-plugin project is then considered incompatible and resolution fails with
 * "No matching variant of project ':lib' was found".
 *
 * The settings plugin registers the rule in every project of the build, so the plain `jar` stays compatible.
 */
class ComposedJarNonPluginConsumerRegressionTest : IntelliJPluginTestBase() {

    @Test
    fun `project without the plugin resolves a plain project dependency of a module`() {
        // A minimal multi-project build:
        //  - `lib` is a plain java-library without the plugin,
        //  - `module` applies the module plugin and depends on `lib`,
        //  - `consumer` doesn't apply the plugin and depends on `module`, so it transitively resolves `lib`
        //    with the `composed-jar` request attached by `module`.
        settingsFile overwrite //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform.settings")
                }

                rootProject.name = "projectName"

                include("lib", "module", "consumer")
                """.trimIndent()

        buildFile overwrite //language=kotlin
                """
                // Root project intentionally left without any plugin.
                """.trimIndent()

        dir.resolve("lib/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    `java-library`
                }
                """.trimIndent()

        dir.resolve("lib/src/main/java/com/example/lib/Lib.java") write //language=java
                """
                package com.example.lib;

                public class Lib {
                }
                """.trimIndent()

        dir.resolve("module/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    `java-library`
                    id("org.jetbrains.intellij.platform.module")
                }

                dependencies {
                    api(project(":lib"))
                }
                """.trimIndent()

        dir.resolve("module/src/main/java/com/example/module/Module.java") write //language=java
                """
                package com.example.module;

                public class Module {
                }
                """.trimIndent()

        dir.resolve("consumer/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    id("java")
                }

                dependencies {
                    implementation(project(":module"))
                }

                val runtimeClasspathFiles = configurations["runtimeClasspath"].incoming.files
                tasks.register("printRuntimeClasspath") {
                    doLast {
                        runtimeClasspathFiles.forEach { println("RUNTIME_CLASSPATH_ENTRY: " + it.name) }
                    }
                }
                """.trimIndent()

        // Referencing classes from both `module` and `lib` makes `:consumer:compileJava` resolve the whole chain.
        dir.resolve("consumer/src/main/java/com/example/consumer/Consumer.java") write //language=java
                """
                package com.example.consumer;

                import com.example.lib.Lib;
                import com.example.module.Module;

                public class Consumer {
                    Lib lib = new Lib();
                    Module module = new Module();
                }
                """.trimIndent()

        build(":consumer:compileJava", ":consumer:printRuntimeClasspath") {
            assertTrue(
                output.contains("RUNTIME_CLASSPATH_ENTRY: lib.jar"),
                "Expected the plain lib jar on the runtime classpath of the non-plugin consumer.",
            )
        }
    }
}
