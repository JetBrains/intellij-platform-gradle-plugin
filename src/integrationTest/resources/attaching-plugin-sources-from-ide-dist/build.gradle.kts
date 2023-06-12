// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
}

intellij {
    type.set("IU")
    version.set("2021.2.4")
    plugins.set(listOf("com.intellij.css"))
    // explicit override of the default false value when the "CI" environment variable exists:
    downloadSources.set(true)
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}
