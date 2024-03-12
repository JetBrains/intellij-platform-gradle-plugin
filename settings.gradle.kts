// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
    }
}

plugins {
    id("com.gradle.enterprise") version("3.16.2")
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.8.0")
}

rootProject.name = "IntelliJ Platform Gradle Plugin"

gradleEnterprise {
    buildScan {
        server = "https://ge.jetbrains.com"
        termsOfServiceUrl = "https://ge.jetbrains.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}
