#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

with(__FILE__.toPath()) {
    runGradleTask(mapOf(
        "org.jetbrains.intellij.buildFeature.checkGradleVersion" to false,
    ), "assemble").let { logs ->
        logs containsText "Build feature is disabled: org.jetbrains.intellij.buildFeature.checkGradleVersion"
    }
    runGradleTask(mapOf(
        "org.jetbrains.intellij.buildFeature.checkGradleIntellijPluginVersion" to false,
    ), "assemble").let { logs ->
        logs containsText "Build feature is disabled: org.jetbrains.intellij.buildFeature.checkGradleIntellijPluginVersion"
    }
    runGradleTask(mapOf(
        "org.jetbrains.intellij.buildFeature.checkGradleVersion" to false,
        "org.jetbrains.intellij.buildFeature.checkGradleIntellijPluginVersion" to false,
    ), "assemble").let { logs ->
        logs containsText "Build feature is disabled: org.jetbrains.intellij.buildFeature.checkGradleVersion"
        logs containsText "Build feature is disabled: org.jetbrains.intellij.buildFeature.checkGradleIntellijPluginVersion"
    }
}
