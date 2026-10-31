// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension

fun listProperty(name: String) = providers.gradleProperty(name).map { value ->
    value.split(',').map(String::trim).filter(String::isNotEmpty)
}.orElse(emptyList())

val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")
val marketplacePluginsProperty = listProperty("marketplacePlugins")
val bundledPluginsProperty = listProperty("bundledPlugins")
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
        plugins(marketplacePluginsProperty)
        bundledPlugins(bundledPluginsProperty)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}

val testSourceSet = sourceSets.named("test")
extensions.getByType<IntelliJPlatformTestingExtension>().testIde.register("testIdeCustom") {
    testFramework(TestFrameworkType.Platform)
    task {
        useJUnit()
        testClassesDirs = testSourceSet.get().output.classesDirs
        classpath += testSourceSet.get().runtimeClasspath
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("expectedPlugins", expectedPluginsProperty.get().joinToString(","))

    doFirst {
        classpath.files.forEach {
            println("test-ide-content-modules: Test classpath entry: ${it.invariantSeparatorsPath}")
        }
    }
}
