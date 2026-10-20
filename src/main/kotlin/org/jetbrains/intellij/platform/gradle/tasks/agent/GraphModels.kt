// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import org.jetbrains.intellij.platform.gradle.Constants.Configurations

internal fun graphConfigurationRoles(name: String): List<String> {
    val normalized = name.lowercase()
    return buildList {
        if ("test" in normalized) {
            add("test")
        }
        if (normalized.startsWith("intellijplatform")) {
            add("platform")
        }
        if (name in setOf(Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE, Configurations.INTELLIJ_PLATFORM_PLUGIN_COMPOSED_MODULE) ||
            "composedjar" in normalized || "distribution" in normalized
        ) {
            add("packaging")
        }
        if ("compile" in normalized || "implementation" in normalized || "api" in normalized || "annotationprocessor" in normalized) {
            add("compile")
        }
        if ("runtime" in normalized) {
            add("runtime")
        }
        if (isEmpty()) {
            add("other")
        }
    }.sorted()
}