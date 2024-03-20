// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.intellij.platform.gradle.Constants.Plugin

/**
 * Creates a JAR file with instrumented classes.
 *
 * @see InstrumentCodeTask
 */
@Deprecated(message = "CHECK")
@CacheableTask
abstract class InstrumentedJarTask : Jar() {

    init {
        group = Plugin.GROUP_NAME
        description = "Assembles an instrumented JAR archive."
    }
}
