#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

with(__FILE__.toPath()) {
    runGradleTask("buildPlugin").let { logs ->
        logs containsText "FAILURE: Build failed with an exception."
        logs containsText " > Both 'localPath' and 'version' specified, second would be ignored"
        logs containsText "BUILD FAILED"
    }
}
