// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

object FileUtils {
    fun expandUserHome(path: String): String {
        if (path == "~") {
            return System.getProperty("user.home")
        }

        if (path.startsWith("~/") || path.startsWith("~\\")) {
            return System.getProperty("user.home") + path.substring(1)
        }

        return path
    }
}
