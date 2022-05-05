#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

with(__FILE__.toPath()) {

    "org.jetbrains.intellij.buildFeature.selfUpdateCheck".let { flag ->
        runGradleTask("assemble", projectProperties = mapOf(flag to false)).let { logs ->
            println(logs.length)
            logs containsText "Build feature is disabled: $flag"
        }
    }

}
