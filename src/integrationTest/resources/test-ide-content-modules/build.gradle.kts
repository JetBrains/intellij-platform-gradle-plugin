// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun listProperty(name: String) = providers.gradleProperty(name).map { value ->
    value.split(',').map(String::trim).filter(String::isNotEmpty)
}.orElse(emptyList())

val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")
val bundledPluginsProperty = listProperty("bundledPlugins")
val bundledModulesProperty = listProperty("bundledModules")
val expectedPluginsProperty = listProperty("expectedPlugins")

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
        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty)
        testFramework(TestFrameworkType.Platform)
        bundledPlugins(bundledPluginsProperty)
        bundledModules(bundledModulesProperty)
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}

tasks {
    test {
        systemProperty("expectedPlugins", expectedPluginsProperty.get().joinToString(","))

        doFirst {
            classpath.files.forEach {
                println("test-ide-content-modules: Test classpath entry: ${it.invariantSeparatorsPath}")
            }
        }
    }
}
