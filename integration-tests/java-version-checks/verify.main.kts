#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

__FILE__.init {
    runGradleTask("clean", "jar", projectProperties = mapOf("intellijVersion" to "2022.2")).let { logs ->
        logs containsText "Java 11 is not supported with IntelliJ Platform 2022.2. Please use Java 17 it you target IntelliJ Platform 2022.2+"
    }
}
