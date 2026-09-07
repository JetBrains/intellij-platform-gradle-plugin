// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "test"

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.1.0"
}

// This is something we must support since a few big corporations requested such feature, because they use similar
// configuration in very big projects.
// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html#configuration.dependencyResolutionManagement
dependencyResolutionManagement {
    // The first one is the default
    //repositoriesMode = RepositoriesMode.PREFER_PROJECT
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    //repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    dependencyResolutionManagement {
        // The first one is the default
        //rulesMode = RulesMode.PREFER_PROJECT
        rulesMode = RulesMode.PREFER_SETTINGS
        //rulesMode = RulesMode.FAIL_ON_PROJECT_RULES
    }

    repositories {
        mavenCentral()

        intellijPlatform {
            defaultRepositories()
        }
    }
}
