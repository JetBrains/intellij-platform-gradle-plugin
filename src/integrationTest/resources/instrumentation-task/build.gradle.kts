// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    id("org.jetbrains.intellij")
}

version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":submodule", "instrumentedJar"))
}

sourceSets {
    main {
        java.srcDirs("customSrc")
    }
}

kotlin {
    jvmToolchain(11)
}

intellij {
    version.set("2022.1.4")
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}
