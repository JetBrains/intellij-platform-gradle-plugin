#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

with(__FILE__.toPath()) {
    runGradleTask("assemble").let { logs ->
        logs matchesRegex ":instrumentation-task-java-only:patchPluginXml .*? completed."
    }
}
