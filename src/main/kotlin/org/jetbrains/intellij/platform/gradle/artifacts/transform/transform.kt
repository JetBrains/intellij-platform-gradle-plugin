// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.gradle.api.artifacts.transform.TransformAction
import org.jetbrains.intellij.platform.gradle.utils.Logger

internal fun TransformAction<*>.runLogging(block: () -> Unit) {
    val log = Logger(javaClass)

    runCatching {
        block()
    }.onFailure {
        log.error("Transformer '${javaClass.canonicalName}' execution failed.", it)
    }
}
