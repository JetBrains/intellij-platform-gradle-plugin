// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
    }
}

plugins {
    id("com.gradle.enterprise") version("3.12.6")
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.5.0")
}

rootProject.name = "gradle-intellij-plugin"

gradleEnterprise {
    buildScan {
        server = "https://ge.jetbrains.com"
        termsOfServiceUrl = "https://ge.jetbrains.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}
