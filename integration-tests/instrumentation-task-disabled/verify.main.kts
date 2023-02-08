#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

import java.nio.file.Files

__FILE__.init {
    runGradleTask("clean", "jar", projectProperties = mapOf("instrumentCode" to false)).let { logs ->
        logs containsText "> Task :instrumentation-task-disabled:instrumentCode SKIPPED"

        buildDirectory containsFile "libs/instrumentation-task-disabled-1.0.0.jar"

        buildDirectory.resolve("classes/java/main").run {
            resolve("ExampleAction.class").run {
                assert(Files.exists(this))
                assert(Files.size(this) == 625L)
            }
            resolve("Main.class").run {
                assert(Files.exists(this))
                assert(Files.size(this) == 658L)
            }
        }
        buildDirectory.resolve("classes/kotlin/main").run {
            resolve("MainKt.class").run {
                assert(Files.exists(this))
                assert(Files.size(this) == 982L)
            }
        }

        buildDirectory.resolve("libs/instrumentation-task-disabled-1.0.0.jar").let { jar ->
            jar containsFileInArchive "META-INF/MANIFEST.MF"

            jar containsFileInArchive "META-INF/plugin.xml"
            jar readEntry "META-INF/plugin.xml" containsText "<action id=\"ExampleAction\" class=\"ExampleAction\" text=\"Example Action\">"

            jar containsFileInArchive "ExampleAction.class"
            assert((jar readEntry "ExampleAction.class").length == 625)

            jar containsFileInArchive "Main.class"
            assert((jar readEntry "Main.class").length == 658)

            jar containsFileInArchive "MainKt.class"
            assert((jar readEntry "MainKt.class").length == 980)

            buildDirectory.resolve("instrumented").run {
                assert(!Files.exists(this))
            }
        }
    }

    runGradleTask("jar", projectProperties = mapOf("instrumentCode" to true)).let { logs ->
        logs containsText "Task ':instrumentation-task-disabled:instrumentCode' is not up-to-date"

        buildDirectory containsFile "libs/instrumentation-task-disabled-1.0.0.jar"

        buildDirectory.resolve("instrumented/instrumentCode").run {
            resolve("ExampleAction.class").run {
                assert(Files.exists(this))
                assert(Files.size(this) == 1028L)
            }
            resolve("Main.class").run {
                assert(Files.exists(this))
                assert(Files.size(this) == 1015L)
            }
            resolve("MainKt.class").run {
                assert(Files.exists(this))
            }
        }

        buildDirectory.resolve("libs/instrumentation-task-disabled-1.0.0.jar").let { jar ->
            jar containsFileInArchive "META-INF/MANIFEST.MF"

            jar containsFileInArchive "META-INF/plugin.xml"
            jar readEntry "META-INF/plugin.xml" containsText "<action id=\"ExampleAction\" class=\"ExampleAction\" text=\"Example Action\">"

            jar containsFileInArchive "ExampleAction.class"
            assert((jar readEntry "ExampleAction.class").length == 625)

            jar containsFileInArchive "Main.class"
            assert((jar readEntry "Main.class").length == 658)

            jar containsFileInArchive "MainKt.class"
            assert((jar readEntry "MainKt.class").length == 980)
        }
    }

    runGradleTask("jar", projectProperties = mapOf("instrumentCode" to false)).let { logs ->
        logs containsText "Task :instrumentation-task-disabled:instrumentCode SKIPPED"

        buildDirectory.resolve("instrumented").run {
            assert(!Files.exists(this))
        }
    }

    runGradleTask("jar", projectProperties = mapOf("instrumentCode" to true)).let { logs ->
        buildDirectory.resolve("instrumented").run {
            assert(Files.isDirectory(this))
            assert(Files.list(this).toArray().isNotEmpty())
        }
    }
}
