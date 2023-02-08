// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.intellij.IntelliJPluginConstants

abstract class InstrumentedJarTask : Jar() {

    init {
        group = IntelliJPluginConstants.PLUGIN_GROUP_NAME
        description = "Assembles an instrumented JAR archive."
    }
}
