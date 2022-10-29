// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
    }
}

rootProject.name = "Gradle IntelliJ Plugin"

includeBuild("/Users/hsz/Projects/JetBrains/gradle-changelog-plugin")
