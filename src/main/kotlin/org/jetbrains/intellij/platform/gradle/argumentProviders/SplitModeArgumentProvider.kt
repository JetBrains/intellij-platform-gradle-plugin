// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.argumentProviders

import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

/**
 * Provides command line arguments for enabling Split Mode.
 */
class SplitModeArgumentProvider(
    @Input val splitMode: Provider<Boolean>,
) : CommandLineArgumentProvider {

    override fun asArguments() = listOfNotNull(
        "splitMode".takeIf { splitMode.get() },
    )
}
