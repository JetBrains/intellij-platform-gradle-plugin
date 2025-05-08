// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
    }

    includeBuild("build-logic")
}

plugins {
    id("com.gradle.develocity") version("3.17.5")
    id("com.autonomousapps.build-health") version("2.17.0")
    id("org.jetbrains.kotlin.jvm") version embeddedKotlinVersion apply false
}

rootProject.name = "IntelliJPlatformGradlePlugin"

val isCI = (System.getenv("CI") ?: "false").toBoolean()

develocity {
    server = "https://ge.jetbrains.com"

    buildScan {
        termsOfUseAgree = "yes"
        publishing.onlyIf { isCI }
    }
}
