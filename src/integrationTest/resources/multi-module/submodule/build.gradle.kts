// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform.module")
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
    implementation("org.jetbrains:dummy:0.1.2")

    intellijPlatform {
        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty)
    }
}

intellijPlatform {
    instrumentCode = false
}
