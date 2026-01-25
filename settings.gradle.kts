// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("com.gradle.develocity") version("4.2")
    id("com.autonomousapps.build-health") version("3.4.0")
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
