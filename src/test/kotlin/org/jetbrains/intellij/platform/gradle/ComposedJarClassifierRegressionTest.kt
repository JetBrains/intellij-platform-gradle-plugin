// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression test for [#1889](https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1889).
 *
 * Before the fix the plugin forced `LibraryElements=composed-jar` on the whole `compileClasspath`,
 * `testCompileClasspath`, and `testRuntimeClasspath`. When an external module that publishes a
 * `composed-jar` variant was pulled both plain and via a transitive `:tests` classifier, Gradle collapsed it
 * to the single `composed-jar` node and dropped the transitive classifier artifact, so `:compileTestJava`
 * failed. The fix requests `composed-jar` per project dependency instead, leaving external modules to
 * standard variant selection, so the classifier artifact survives.
 */
class ComposedJarClassifierRegressionTest : IntelliJPluginTestBase() {

    private val localRepository
        get() = dir.resolve("local-repository").invariantSeparatorsPathString

    @Test
    fun `transitive classifier dependency on an external composed-jar module stays on the test classpath`() {
        // A minimal multi-project build:
        //  - `composed` and `consumer` are plain java-library producers published to a local Maven repository,
        //    mimicking modules built by this plugin (they expose a `composed-jar` variant),
        //  - `app` applies the module plugin and consumes `consumer`, which transitively pulls `composed` both
        //    plain and via a `:tests` classifier.
        settingsFile overwrite //language=kotlin
                """
                rootProject.name = "projectName"

                plugins {
                    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
                }

                include("composed", "consumer", "app")
                """.trimIndent()

        buildFile overwrite //language=kotlin
                """
                // Root project intentionally left without any plugin.
                """.trimIndent()

        // Producer that publishes both a regular `jar` and an additional `composed-jar` variant, plus a
        // `:tests` classifier artifact that is not exposed as a variant.
        dir.resolve("composed/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    `java-library`
                    `maven-publish`
                }

                group = "com.example"
                version = "1.0"

                sourceSets.create("classifier")

                val composedJar = tasks.register<Jar>("composedJar") {
                    archiveClassifier = "composed"
                    from(sourceSets.main.get().output)
                }

                val testsJar = tasks.register<Jar>("testsJar") {
                    archiveClassifier = "tests"
                    from(sourceSets.getByName("classifier").output)
                }

                fun composedVariant(usage: String) = Action<org.gradle.api.artifacts.Configuration> {
                    isCanBeResolved = false
                    isCanBeConsumed = true
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(usage))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
                        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("composed-jar"))
                    }
                    outgoing.artifact(composedJar)
                }

                val composedApiElements = configurations.register("composedApiElements", composedVariant(Usage.JAVA_API))
                val composedRuntimeElements = configurations.register("composedRuntimeElements", composedVariant(Usage.JAVA_RUNTIME))

                val javaComponent = components["java"] as org.gradle.api.component.AdhocComponentWithVariants
                javaComponent.addVariantsFromConfiguration(composedApiElements.get()) { mapToOptional() }
                javaComponent.addVariantsFromConfiguration(composedRuntimeElements.get()) { mapToOptional() }

                publishing {
                    publications {
                        create<MavenPublication>("maven") {
                            from(javaComponent)
                            artifact(testsJar)
                        }
                    }
                    repositories {
                        maven { url = uri("${localRepository}") }
                    }
                }
                """.trimIndent()

        dir.resolve("composed/src/main/java/com/example/composed/Composed.java") write //language=java
                """
                package com.example.composed;

                public class Composed {
                }
                """.trimIndent()

        dir.resolve("composed/src/classifier/java/com/example/composed/ComposedTestSupport.java") write //language=java
                """
                package com.example.composed;

                public class ComposedTestSupport {
                }
                """.trimIndent()

        // Producer that transitively depends on `composed` both plain and via a `:tests` classifier, exactly
        // like the reporter's `abc` module. Publishing records the classifier dependency in Gradle Module
        // Metadata as a `thirdPartyCompatibility` artifact selector.
        dir.resolve("consumer/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    `java-library`
                    `maven-publish`
                }

                group = "com.example"
                version = "1.0"

                repositories {
                    mavenCentral()
                    maven { url = uri("${localRepository}") }
                }

                dependencies {
                    api("com.example:composed:1.0")
                    api("com.example:composed:1.0:tests")
                }

                publishing {
                    publications {
                        create<MavenPublication>("maven") {
                            from(components["java"])
                        }
                    }
                    repositories {
                        maven { url = uri("${localRepository}") }
                    }
                }
                """.trimIndent()

        dir.resolve("consumer/src/main/java/com/example/consumer/Consumer.java") write //language=java
                """
                package com.example.consumer;

                public class Consumer {
                }
                """.trimIndent()

        // The actual plugin consumer. It resolves `consumer` on `testImplementation`, which pulls `composed`
        // plain and `composed:tests` transitively.
        dir.resolve("app/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    id("java")
                    id("org.jetbrains.intellij.platform.module")
                }

                repositories {
                    mavenCentral()
                    maven { url = uri("${localRepository}") }
                }

                dependencies {
                    testImplementation("com.example:consumer:1.0")
                }

                val testCompileClasspathFiles = configurations["testCompileClasspath"].incoming.files
                tasks.register("printTestCompileClasspath") {
                    doLast {
                        testCompileClasspathFiles.forEach { println("TEST_COMPILE_CLASSPATH_ENTRY: " + it.name) }
                    }
                }
                """.trimIndent()

        // Test source referencing both the plain and the `:tests` classifier classes, so `:app:compileTestJava`
        // only succeeds when both jars are on the test classpath.
        dir.resolve("app/src/test/java/com/example/app/AppTest.java") write //language=java
                """
                package com.example.app;

                import com.example.composed.Composed;
                import com.example.composed.ComposedTestSupport;

                public class AppTest {
                    Composed composed = new Composed();
                    ComposedTestSupport support = new ComposedTestSupport();
                }
                """.trimIndent()

        build(":composed:publish")
        build(":consumer:publish")

        build(":app:printTestCompileClasspath", ":app:compileTestJava") {
            assertTrue(
                output.contains("TEST_COMPILE_CLASSPATH_ENTRY: composed-1.0.jar"),
                "Expected the plain composed jar on the test compile classpath.",
            )
            assertTrue(
                output.contains("TEST_COMPILE_CLASSPATH_ENTRY: composed-1.0-tests.jar"),
                "Expected the transitive ':tests' classifier jar on the test compile classpath (#1889).",
            )
        }
    }
}
