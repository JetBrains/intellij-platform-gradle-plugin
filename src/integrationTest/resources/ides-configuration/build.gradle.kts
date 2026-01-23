// Copyright 2000-2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.platform.gradle.extensions.excludeCoroutines
import org.jetbrains.intellij.platform.gradle.extensions.parseIdeNotation

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
