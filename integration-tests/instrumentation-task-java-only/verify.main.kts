#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

val logs = runGradleTask("assemble")

logs matchesRegex ":instrumentation-task-java-only:patchPluginXml .*? completed."
