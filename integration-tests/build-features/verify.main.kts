// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#!/usr/bin/env -S kotlin -J-ea

@file:Import("../verify.utils.kts")

with(__FILE__.toPath()) {

    "org.jetbrains.intellij.buildFeature.selfUpdateCheck".let { flag ->
        runGradleTask("assemble", projectProperties = mapOf(flag to false)).let { logs ->
            println(logs.length)
            logs containsText "Build feature is disabled: $flag"
        }
    }

}
