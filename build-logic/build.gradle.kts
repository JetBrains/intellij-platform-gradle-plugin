// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version embeddedKotlinVersion
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.xmlutil.core)
    implementation(libs.xmlutil.serialization) {
        exclude("io.github.pdvrieze.xmlutil", "core")
    }
    implementation(libs.kotlinx.serialization.json)
}

gradlePlugin {
    plugins {
        register("build-logic") {
            id = "build-logic"
            implementationClass = "BuildLogicPlugin"
        }
    }
}
