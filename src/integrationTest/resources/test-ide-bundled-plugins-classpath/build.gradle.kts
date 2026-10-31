// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun listProperty(name: String) = providers.gradleProperty(name).map { value ->
    value.split(',').map(String::trim).filter(String::isNotEmpty)
}.orElse(emptyList())

val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")
val useInstallerProperty = providers.gradleProperty("useInstaller").map(String::toBoolean).orElse(true)
val bundledPluginsProperty = listProperty("bundledPlugins")
val testBundledPluginsProperty = listProperty("testBundledPlugins")
val expectedPluginsProperty = listProperty("expectedPlugins")
val testClassesProperty = listProperty("testClasses")

version = "1.0.0"

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(21)
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
        testFramework(TestFrameworkType.Platform)
        bundledPlugins(bundledPluginsProperty)
        testBundledPlugins(testBundledPluginsProperty)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}

tasks {
    test {
        systemProperty("expectedPlugins", expectedPluginsProperty.get().joinToString(","))

        filter {
            testClassesProperty.get().forEach(::includeTestsMatching)
        }

        doFirst {
            classpath.files.forEach {
                println("test-ide-bundled-plugins-classpath: Test classpath entry: ${it.invariantSeparatorsPath}")
            }
        }
    }
}
