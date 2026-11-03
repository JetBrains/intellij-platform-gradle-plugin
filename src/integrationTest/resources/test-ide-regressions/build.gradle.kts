// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension

fun listProperty(name: String) = providers.gradleProperty(name).map { value ->
    value.split(',').map(String::trim).filter(String::isNotEmpty)
}.orElse(emptyList())

fun booleanProperty(name: String, defaultValue: Boolean) = providers.gradleProperty(name).map(String::toBoolean).orElse(defaultValue)

fun testFrameworkType(name: String) = when (name) {
    "Platform" -> TestFrameworkType.Platform
    "JUnit5" -> TestFrameworkType.JUnit5
    "Plugin.Java" -> TestFrameworkType.Plugin.Java
    "Bundled" -> TestFrameworkType.Bundled
    else -> throw IllegalArgumentException("Unsupported test framework type: $name")
}

val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")
val useInstallerProperty = booleanProperty("useInstaller", true)
val javaReleaseProperty = providers.gradleProperty("javaRelease").map(String::toInt).orElse(21)
val testFrameworksProperty = listProperty("testFrameworks").map { it.ifEmpty { listOf("Platform") } }
val bundledPluginsProperty = listProperty("bundledPlugins")
val testBundledPluginsProperty = listProperty("testBundledPlugins")
val disabledPluginsProperty = listProperty("disabledPlugins")
val opentest4jProperty = booleanProperty("opentest4j", true)
val junitPlatformProperty = booleanProperty("junitPlatform", false)
val junitVersionProperty = providers.gradleProperty("junitVersion").orElse("5.10.2")
val testClassesProperty = listProperty("testClasses")
val expectedBuildProperty = providers.gradleProperty("expectedBuild").orElse("")
val customTestIdeTypeProperty = providers.gradleProperty("customTestIde.type")
val customTestIdeVersionProperty = providers.gradleProperty("customTestIde.version")
val customTestIdeExpectedBuildProperty = providers.gradleProperty("customTestIde.expectedBuild").orElse("")

// Gradle properties passed to the test JVM as system properties and verified by the tests, see SmokeTest
val testSystemProperties = listOf(
    "expectedPlugins",
    "unexpectedPlugins",
    "expectedFileTypes",
    "expectedServices",
    "yamlExpected",
    "fileComparisonDataExpected",
).associateWith { providers.gradleProperty(it).orElse("") }

version = "1.0.0"

plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

// The tests run with the JetBrains Runtime of the targeted IntelliJ Platform, which is Java 17 up to 2024.1
tasks.withType<JavaCompile>().configureEach {
    options.release = javaReleaseProperty
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty) {
            useInstaller = useInstallerProperty
        }
        // The IntelliJ Platform resolved from the IntelliJ Maven Repository doesn't bundle the JetBrains Runtime
        if (!useInstallerProperty.get()) {
            jetbrainsRuntime()
        }
        testFrameworks(testFrameworksProperty.get().map(::testFrameworkType))
        bundledPlugins(bundledPluginsProperty)
        testBundledPlugins(testBundledPluginsProperty)
    }

    testImplementation("junit:junit:4.13.2")
    if (opentest4jProperty.get()) {
        testImplementation("org.opentest4j:opentest4j:1.3.0")
    }
    if (junitPlatformProperty.get()) {
        testImplementation(platform("org.junit:junit-bom:${junitVersionProperty.get()}"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "223"
            untilBuild = provider { null }
        }
    }
}

val testSourceSet = sourceSets.named("test")

if (customTestIdeTypeProperty.isPresent) {
    extensions.getByType<IntelliJPlatformTestingExtension>().testIde.register("testIdeCustom") {
        type = customTestIdeTypeProperty.map { IntelliJPlatformType.fromCode(it) }
        version = customTestIdeVersionProperty
        useInstaller = true
        testFramework(TestFrameworkType.Platform)

        task {
            useJUnit()
            testClassesDirs = testSourceSet.get().output.classesDirs
            classpath += testSourceSet.get().runtimeClasspath
            systemProperty("expectedBuild", customTestIdeExpectedBuildProperty.get())
        }
    }
}

tasks {
    test {
        systemProperty("expectedBuild", expectedBuildProperty.get())

        if (junitPlatformProperty.get()) {
            useJUnitPlatform()
        }
    }

    prepareTestSandbox {
        disabledPlugins.addAll(disabledPluginsProperty)
    }
}

tasks.withType<Test>().configureEach {
    providers.environmentVariable("WINDOWS_DIAGNOSTIC_PHASE").orNull?.let { phase ->
        if (phase == "candidate") {
            systemProperty("code.provenance.chatter.analytics.enabled", "false")
        }
    }

    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    testSystemProperties.forEach { (name, value) ->
        systemProperty(name, value.get())
    }

    filter {
        testClassesProperty.get().forEach(::includeTestsMatching)
    }

    val taskName = name
    doFirst {
        classpath.files.forEach {
            println("test-ide-regressions: Test classpath entry [$taskName]: ${it.invariantSeparatorsPath}")
        }
    }
}
