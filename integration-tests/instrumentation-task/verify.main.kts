#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

import java.nio.file.Files

with(__FILE__.toPath()) {
    runGradleTask("clean", "jar").let { logs ->
        logs containsText "[ant:instrumentIdeaExtensions] Added @NotNull assertions to 1 files"

        buildDirectory.resolve("classes/java/main/Main.class").run {
            assert(Files.exists(this))
            assert(Files.size(this) == 658L)
        }
        buildDirectory.resolve("classes/java/main-instrumented/Main.class").run {
            assert(Files.exists(this))
            assert(Files.size(this) == 1015L)
        }
        buildDirectory.resolve("classes/kotlin/main/MainKt.class").run {
            assert(Files.exists(this))
            assert(Files.size(this) == 973L)
        }
        buildDirectory.resolve("classes/kotlin/main/MainKt\$Companion.class").run {
            assert(Files.exists(this))
            assert(Files.size(this) == 1323L)
        }

        buildDirectory.resolve("libs/instrumentation-task-1.0.0.jar").let { jar ->
            jar containsFileInArchive "Main.class"
            assert((jar readEntry "Main.class").length == 1015)

            jar containsFileInArchive "MainKt.class"
            assert((jar readEntry "MainKt.class").length == 971)

            jar containsFileInArchive "MainKt\$Companion.class"
            assert((jar readEntry "MainKt\$Companion.class").length == 1319)
        }
    }
}
