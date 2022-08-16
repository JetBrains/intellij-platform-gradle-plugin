#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

__FILE__.init {
    runGradleTask("clean", "jar", projectProperties = mapOf("intellijVersion" to "2019.3")).let { logs ->
        logs containsText "Java 11 is not supported. Please use Java 8 it you target IntelliJ Platform lower than 2020.3"
    }

    runGradleTask("clean", "jar", projectProperties = mapOf("intellijVersion" to "2022.2")).let { logs ->
        logs containsText "Java 11 is not supported. Please use Java 17 it you target IntelliJ Platform 2022.2+"
    }
}
