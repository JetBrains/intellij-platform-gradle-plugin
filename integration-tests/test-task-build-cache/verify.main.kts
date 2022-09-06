#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

import java.nio.file.Files

with(__FILE__.toPath()) {
    buildCacheDirectory.takeIf { Files.exists(it) }?.deleteRecursively()

    runGradleTask("clean", "test", additionalSwitches = listOf("--build-cache")).let { logs ->
        logs containsText "Task ':test-task-build-cache:test' is not up-to-date"
        logs containsText "Stored cache entry for task ':test-task-build-cache:test' with cache key"
    }

    buildDirectory containsFile "idea-sandbox/system-test/index/stubs"

    buildDirectory.deleteRecursively()

    runGradleTask("test", additionalSwitches = listOf("--build-cache")).let { logs ->
        logs containsText "Task ':test' is not up-to-date"
    }
}
