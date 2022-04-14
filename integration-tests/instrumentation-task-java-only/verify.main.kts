#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

logs matchesRegex ":instrumentation-task-java-only:patchPluginXml .*? completed."
