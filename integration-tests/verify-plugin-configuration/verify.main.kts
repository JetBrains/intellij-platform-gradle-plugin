#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

import java.nio.file.Files

__FILE__.init {
    val userHome = projectDirectory.resolve("home")
    val downloadDir = buildDirectory.resolve("home")
    val ides = userHome.resolve(".pluginVerifier/ides").also {
        Files.deleteIfExists(it.resolve("foo"))
    }

    val defaultSystemProperties = mapOf(
        "user.home" to userHome,
    )
    val defaultProjectProperties = mapOf(
        "intellijVersion" to "2022.2",
        "sinceBuild" to "222",
        "languageVersion" to "17",
        "downloadDir" to projectDirectory.resolve("home"),
    )

    runGradleTask(
        "clean", "verifyPluginConfiguration", systemProperties = defaultSystemProperties, projectProperties = defaultProjectProperties
    ).let { logs ->
        logs notContainsText "[gradle-intellij-plugin :verify-plugin-configuration:verifyPluginConfiguration] The following plugin configuration issues were found:"
    }

    runGradleTask(
        "clean", "verifyPluginConfiguration", systemProperties = defaultSystemProperties, projectProperties = defaultProjectProperties + mapOf(
            "languageVersion" to "11",
        )
    ).let { logs ->
        logs containsText "[gradle-intellij-plugin :verify-plugin-configuration:verifyPluginConfiguration] The following plugin configuration issues were found:"
        logs containsText "- The Java configuration specifies sourceCompatibility=11 but IntelliJ Platform 2022.2 requires sourceCompatibility=17."
    }

    runGradleTask(
        "clean", "verifyPluginConfiguration", systemProperties = defaultSystemProperties, projectProperties = defaultProjectProperties + mapOf(
            "sinceBuild" to "203",
        )
    ).let { logs ->
        logs containsText "[gradle-intellij-plugin :verify-plugin-configuration:verifyPluginConfiguration] The following plugin configuration issues were found:"
        logs containsText "- The 'since-build' property is lower than the target IntelliJ Platform major version: 203 < 222."
        logs containsText "- The Java configuration specifies targetCompatibility=17 but since-build='203' property requires targetCompatibility=11."
    }

    ides.also {
        Files.createDirectories(it)
        if (!Files.exists(it.resolve("foo"))) {
            Files.createFile(it.resolve("foo"))
        }
    }
    runGradleTask(
        "clean", "verifyPluginConfiguration", systemProperties = defaultSystemProperties, projectProperties = defaultProjectProperties + mapOf(
            "downloadDir" to downloadDir,
        )
    ).let { logs ->
        logs containsText "[gradle-intellij-plugin :verify-plugin-configuration:verifyPluginConfiguration] The following plugin configuration issues were found:"
        logs containsText "- The Plugin Verifier download directory is set to $downloadDir, but downloaded IDEs were also found in $ides, see: https://jb.gg/intellij-platform-plugin-verifier-old-download-dir"
    }
}
