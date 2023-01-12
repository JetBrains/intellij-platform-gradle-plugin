#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

import java.nio.file.Files

__FILE__.init {
    "org.jetbrains.intellij.buildFeature.selfUpdateCheck".let { flag ->
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

    "org.jetbrains.intellij.buildFeature.paidPluginSearchableOptionsWarning".let { flag ->
        runGradleTask(
            "clean", "buildSearchableOptions", projectProperties = mapOf(
                "buildSearchableOptionsEnabled" to true,
                flag to false,
            )
        ).let { logs ->
            logs containsText "Build feature is disabled: $flag"
        }

        val pluginXml = projectDirectory.resolve("src/main/resources/META-INF/plugin.xml").also {
            Files.createDirectories(it.parent)
            Files.deleteIfExists(it)
            Files.createFile(it)
            Files.writeString(
                it,
                """
                <idea-plugin>
                    <id>test</id>
                    <name>Test</name>
                    <vendor>JetBrains</vendor>
                    <product-descriptor code="GIJP" release-date="20220701" release-version="20221"/>
                </idea-plugin>
                """.trimIndent()
            )
        }

        runGradleTask(
            "clean", "buildSearchableOptions", projectProperties = mapOf(
                "buildSearchableOptionsEnabled" to true,
                flag to true,
            )
        ).let { logs ->
            logs containsText "Due to IDE limitations, it is impossible to run the IDE in headless mode to collect searchable options for a paid plugin."
        }

        Files.delete(pluginXml)
    }
}
