// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import org.gradle.process.JavaForkOptions

internal data class HeapSpaceOverrides(
    val max: Boolean,
    val min: Boolean,
)

internal fun JavaForkOptions.heapSpaceArguments() = listOfNotNull(
    maxHeapSize?.let { "-Xmx$it" },
    minHeapSize?.let { "-Xms$it" },
)

internal fun JavaForkOptions.heapSpaceOverrides(): HeapSpaceOverrides {
    val inheritedJvmArguments = jvmArgs.orEmpty()

    return HeapSpaceOverrides(
        max = maxHeapSize != null || inheritedJvmArguments.any { it.startsWith("-Xmx") },
        min = minHeapSize != null || inheritedJvmArguments.any { it.startsWith("-Xms") },
    )
}

internal fun List<String>.filterOverriddenHeapSpace(overrides: HeapSpaceOverrides) = filterNot {
    (overrides.max && it.startsWith("-Xmx")) || (overrides.min && it.startsWith("-Xms"))
}
