// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
}

intellij {
    version.set("2022.1.4")
}
