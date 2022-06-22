#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

import java.nio.file.Files

__FILE__.init {
    runGradleTask("clean", "jar").let { logs ->
        logs containsText "[ant:instrumentIdeaExtensions] Added @NotNull assertions to 3 files"

        buildDirectory.resolve("classes/java/main/Main.class").run {
            assert(Files.exists(this))
            assert(Files.size(this) == 658L)
        }
        buildDirectory.resolve("classes/java/main/CustomMain.class").run {
            assert(Files.exists(this))
            assert(Files.size(this) == 683L)
        }
        buildDirectory.resolve("tmp/instrumentCode/Main.class").run {
            assert(Files.exists(this))
            assert(Files.size(this) == 1015L)
        }
        buildDirectory.resolve("classes/java/main/Form.class").run {
            assert(Files.exists(this))
            assert(Files.size(this) == 482L)
        }
        buildDirectory.resolve("tmp/instrumentCode/Form.class").run {
            assert(Files.exists(this))
            assert(Files.size(this) == 1269L)
        }
        buildDirectory.resolve("classes/kotlin/main/MainKt.class").run {
            assert(Files.exists(this))
            assert(Files.size(this) == 793L)
        }

        buildDirectory.resolve("libs/instrumentation-task-1.0.0.jar").let { jar ->
            jar containsFileInArchive "Main.class"
            assert((jar readEntry "Main.class").length == 1015)

            jar containsFileInArchive "Form.class"
            assert((jar readEntry "Form.class").length == 1269)

            jar containsFileInArchive "MainKt.class"
            assert((jar readEntry "MainKt.class").length == 1185)

            jar containsFileInArchive "CustomMain.class"
            assert((jar readEntry "CustomMain.class").length == 1040)
        }
    }

    runGradleTask("jar").let { logs ->
        logs containsText "> Task :instrumentation-task:compileKotlin UP-TO-DATE"
        logs containsText "> Task :instrumentation-task:compileJava UP-TO-DATE"
    }

    projectDirectory.resolve("src/main/kotlin/MainKt.kt").toFile().appendText("// foo\n")

    runGradleTask("jar").let { logs ->
        logs containsText "Task ':instrumentation-task:compileKotlin' is not up-to-date"
        logs containsText "> Task :instrumentation-task:compileJava UP-TO-DATE"
    }

    projectDirectory.resolve("src/main/java/Main.java").toFile().appendText("// foo\n")

    runGradleTask("jar").let { logs ->
        logs containsText "Task ':instrumentation-task:compileKotlin' is not up-to-date"
        logs containsText "Task ':instrumentation-task:compileJava' is not up-to-date"
    }
}
