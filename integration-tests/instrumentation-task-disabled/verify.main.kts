#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

import java.nio.file.Files

with(__FILE__.toPath()) {
    runGradleTask("clean", "jar").let { logs ->
        logs containsText "> Task :instrumentation-task-disabled:instrumentCode SKIPPED"
    }

    buildDirectory containsFile "libs/instrumentation-task-disabled-1.0.0.jar"

    buildDirectory.resolve("classes/java/main/ExampleAction.class").run {
        assert(Files.exists(this))
        assert(Files.size(this) == 625L)
    }
    buildDirectory.resolve("classes/java/main/Main.class").run {
        assert(Files.exists(this))
        assert(Files.size(this) == 658L)
    }
    buildDirectory.resolve("classes/kotlin/main/MainKt.class").run {
        assert(Files.exists(this))
        assert(Files.size(this) == 982L)
    }
    buildDirectory.resolve("classes/kotlin/main/MainKt\$Companion.class").run {
        assert(Files.exists(this))
        assert(Files.size(this) == 1332L)
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

        jar containsFileInArchive "MainKt\$Companion.class"
        assert((jar readEntry "MainKt\$Companion.class").length == 1328)
    }
}
