#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

with(__FILE__.toPath()) {
    val propertyName = "org.jetbrains.intellij.buildFeature.selfUpdateCheck"
    runGradleTask(
        mapOf(propertyName to false), "assemble"
    ).let { logs ->
        logs containsText "Build feature is disabled: $propertyName"
    }
}
