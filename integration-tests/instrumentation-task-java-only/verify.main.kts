#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

runGradleTask("assemble").let { logs ->
    logs matchesRegex ":instrumentation-task-java-only:patchPluginXml .*? completed."
}
