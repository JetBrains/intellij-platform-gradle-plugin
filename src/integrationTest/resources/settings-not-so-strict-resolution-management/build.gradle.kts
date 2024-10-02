// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")

version = "1.0.0"

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()

    intellijPlatform {
        //jetbrainsRuntime()
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty)
        //instrumentationTools()
        testFramework(TestFrameworkType.Platform)

        // Use ./gradlew printBundledPlugins
        bundledPlugins(
            "AntSupport",
            "Coverage",
            "DevKit",
            "Git4Idea",
            "JUnit"
        )
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}


