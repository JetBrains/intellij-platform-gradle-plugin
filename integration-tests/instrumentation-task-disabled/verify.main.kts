#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

runGradleTask("buildPlugin").let { logs ->
    logs matchesRegex ":instrumentation-task-disabled:buildPlugin .*? completed."
    logs containsText "> Task :instrumentation-task-disabled:instrumentCode SKIPPED"
}

buildDirectory containsFile "libs/instrumentation-task-disabled-1.0.0.jar"

buildDirectory.resolve("libs/instrumentation-task-disabled-1.0.0.jar").let { jar ->
    jar containsFileInArchive "META-INF/MANIFEST.MF"

    jar containsFileInArchive "META-INF/plugin.xml"
    jar readEntry "META-INF/plugin.xml" containsText "<action id=\"ExampleAction\" class=\"ExampleAction\" text=\"Example Action\">"

    jar containsFileInArchive "ExampleAction.class"
}
