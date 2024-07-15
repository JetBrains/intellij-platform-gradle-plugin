import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

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
    implementation(libs.xmlutil.serialization)
    implementation(libs.kotlinx.serialization.json)

    constraints {
        listOf(libs.xmlutil.core, libs.xmlutil.serialization).forEach {
            implementation(it) {
                attributes { attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm) }
            }
        }
    }
}

gradlePlugin {
    plugins {
        register("build-logic") {
            id = "build-logic"
            implementationClass = "BuildLogicPlugin"
        }
    }
}
