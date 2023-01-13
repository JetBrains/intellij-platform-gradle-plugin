#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

import java.nio.file.Files

__FILE__.init {
    runGradleTask("dependencies").let { logs ->
        val safeLogs = logs.lineSequence().filterNot { it.startsWith("[gradle-intellij-plugin") }.joinToString("\n")

        safeLogs containsText """
            \--- org.jetbrains:markdown:0.3.1
                 \--- org.jetbrains:markdown-jvm:0.3.1
                      +--- org.jetbrains.kotlin:kotlin-stdlib:1.5.31
                      |    +--- org.jetbrains:annotations:13.0
                      |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.5.31
                      \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.5.31
        """.trimIndent()
    }

    runGradleTask("setupDependencies", "dependencies").let { logs ->
        val safeLogs = logs.lineSequence().filterNot { it.startsWith("[gradle-intellij-plugin") }.joinToString("\n")

        safeLogs containsText """
            +--- org.jetbrains:markdown:0.3.1
            |    \--- org.jetbrains:markdown-jvm:0.3.1
            |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.5.31
            |         |    +--- org.jetbrains:annotations:13.0 -> 24.0.0
            |         |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.5.31
            |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.5.31
            +--- org.jetbrains:annotations:24.0.0
            \--- com.jetbrains:ideaIC:2022.1
        """.trimIndent()

        logs containsText """
            implementation - Implementation only dependencies for compilation 'main' (target  (jvm)). (n)
            \--- org.jetbrains:markdown:0.3.1 (n)
        """.trimIndent()

        logs containsText """
            z10_intellijDefaultDependencies
            \--- org.jetbrains:annotations:24.0.0
        """.trimIndent()

        logs containsText """
            z90_intellij
            \--- com.jetbrains:ideaIC:2022.1
        """.trimIndent()
    }

    runGradleTask("clean", "build").let { logs ->
        buildDirectory.resolve("jacoco/test.exec").let { jacocoTestExec ->
            assert(Files.exists(jacocoTestExec)) { "expect that $jacocoTestExec exists" }
        }

        buildDirectory.resolve("reports/jacoco.xml").let { jacocoXml ->
            assert(Files.exists(jacocoXml))  { "expect that $jacocoXml exists" }

            jacocoXml containsText """
                <method name="getRandomNumber" desc="()I" line="7">
                    <counter type="INSTRUCTION" missed="0" covered="2"/>
                    <counter type="LINE" missed="0" covered="1"/>
                    <counter type="COMPLEXITY" missed="0" covered="1"/>
                    <counter type="METHOD" missed="0" covered="1"/>
                </method>
            """.lines().joinToString("", transform = String::trim)
        }
    }
}
