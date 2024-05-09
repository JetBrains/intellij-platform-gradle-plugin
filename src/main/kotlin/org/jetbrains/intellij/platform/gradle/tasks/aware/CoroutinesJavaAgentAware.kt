// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.jetbrains.intellij.platform.gradle.argumentProviders.IntelliJPlatformArgumentProvider

/**
 * Provides the path to the Java Agent file for the Coroutines library required to enable coroutines debugging.
 *
 * @see RunnableIdeAware
 * @see TestableAware
 */
interface CoroutinesJavaAgentAware {

    /**
     * The path to the coroutines Java Agent file.
     *
     * @see IntelliJPlatformArgumentProvider
     */
    @get:Internal
    @get:Optional
    val coroutinesJavaAgentFile: RegularFileProperty
}
