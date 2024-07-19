// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
    }

    includeBuild("build-logic")
}

plugins {
    id("com.gradle.develocity") version("3.17.5")
}

rootProject.name = "IntelliJPlatformGradlePlugin"

develocity {
    server = "https://ge.jetbrains.com"

    buildScan {
        termsOfUseUrl = "https://ge.jetbrains.com/terms-of-service"
        termsOfUseAgree = "yes"
    }
}
