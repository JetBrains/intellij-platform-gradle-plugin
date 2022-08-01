#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

__FILE__.init {
    runGradleTask("dependencies").let { logs ->
        val safeLogs = logs.lineSequence().filterNot { it.startsWith("[gradle-intellij-plugin") }.joinToString("\n")

        safeLogs containsText """
            +--- org.jetbrains:markdown:0.3.1
            |    \--- org.jetbrains:markdown-jvm:0.3.1
            |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.5.31
            |         |    +--- org.jetbrains:annotations:13.0 -> 23.0.0
            |         |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.5.31
            |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.5.31
            +--- org.jetbrains:annotations:23.0.0
            \--- com.jetbrains:ideaIC:2022.1
        """.trimIndent()

        logs containsText """
            implementation - Implementation only dependencies for compilation 'main' (target  (jvm)). (n)
            \--- org.jetbrains:markdown:0.3.1 (n)
        """.trimIndent()

        logs containsText """
            z10_intellijDefaultDependencies
            \--- org.jetbrains:annotations:23.0.0
        """.trimIndent()

        logs containsText """
            z90_intellij
            \--- com.jetbrains:ideaIC:2022.1
        """.trimIndent()
    }
}
