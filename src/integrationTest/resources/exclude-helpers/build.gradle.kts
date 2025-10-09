// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.platform.gradle.extensions.excludeCoroutines

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.kotest:kotest-assertions-core:5.9.1") {
        excludeCoroutines()
    }
}
