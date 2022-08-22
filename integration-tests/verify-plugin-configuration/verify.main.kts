#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

__FILE__.init {
    runGradleTask(
        "clean", "verifyPluginConfiguration", projectProperties = mapOf(
            "intellijVersion" to "2022.2",
            "sinceBuild" to "222",
            "languageVersion" to "11",
        )
    ).let { logs ->
        logs containsText "[gradle-intellij-plugin :verify-plugin-configuration:verifyPluginConfiguration] The following compatibility configuration issues were found:"
        logs containsText "- The Java configuration specifies sourceCompatibility=11 but IntelliJ Platform 2022.2 requires sourceCompatibility=17."
    }

    runGradleTask(
        "clean", "verifyPluginConfiguration", projectProperties = mapOf(
            "intellijVersion" to "2022.2",
            "sinceBuild" to "203",
            "languageVersion" to "17",
        )
    ).let { logs ->
        logs containsText "[gradle-intellij-plugin :verify-plugin-configuration:verifyPluginConfiguration] The following compatibility configuration issues were found:\n"
        logs containsText "- The 'since-build' property is lower than the target IntelliJ Platform major version: 203 < 222."
        logs containsText "- The Java configuration specifies targetCompatibility=17 but since-build='203' property requires targetCompatibility=11."
    }
}
