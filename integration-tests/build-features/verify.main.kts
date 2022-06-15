#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

with(__FILE__.toPath()) {
    "org.jetbrains.intellij.buildFeature.selfUpdateCheck".let { flag ->
        runGradleTask("assemble", projectProperties = mapOf(flag to false)).let { logs ->
            logs containsText "Build feature is disabled: $flag"
        }
    }

    "org.jetbrains.intellij.buildFeature.useDependencyFirstResolutionStrategy".let { flag ->
        runGradleTask("assemble", projectProperties = mapOf(flag to false)).let { logs ->
            logs containsText "Build feature is disabled: $flag"
        }
    }

    "org.jetbrains.intellij.buildFeature.noSearchableOptionsWarning".let { flag ->
        runGradleTask(
            "clean", "jarSearchableOptions", projectProperties = mapOf(
                "buildSearchableOptionsEnabled" to true,
                flag to false,
            )
        ).let { logs ->
            logs containsText "Build feature is disabled: $flag"
        }
        runGradleTask(
            "clean", "jarSearchableOptions", projectProperties = mapOf(
                "buildSearchableOptionsEnabled" to true,
                flag to true,
            )
        ).let { logs ->
            logs containsText "No searchable options found."
        }
    }
}
