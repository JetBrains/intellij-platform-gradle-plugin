import java.time.Duration
import java.time.temporal.ChronoUnit

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")
val splitModeProperty = providers.gradleProperty("splitMode").map { it == "true" }

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
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty)
        instrumentationTools()
    }
}

intellijPlatform {
    instrumentCode = false
    buildSearchableOptions = false
}

tasks {
    runIde {
        splitMode = splitModeProperty
    }
}
