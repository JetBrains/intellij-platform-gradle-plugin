// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.platform.gradle.tasks.CustomRunIdeTask
import org.jetbrains.intellij.platform.gradle.tasks.CustomTestIdeTask

val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")

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
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}

tasks {
    runIde {
        enabled = false
    }
}

val customRunIde by tasks.registering(CustomRunIdeTask::class) {
    enabled = false
}

val customTestIde by tasks.registering(CustomTestIdeTask::class) {
    enabled = false
}
