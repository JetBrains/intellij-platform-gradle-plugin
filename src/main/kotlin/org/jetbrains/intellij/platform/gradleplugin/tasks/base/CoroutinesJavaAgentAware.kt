// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks.base

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal

interface CoroutinesJavaAgentAware {

    /**
     * Represents the path to the coroutines Java agent file.
     */
    @get:Internal
    val coroutinesJavaAgentFile: RegularFileProperty
}
