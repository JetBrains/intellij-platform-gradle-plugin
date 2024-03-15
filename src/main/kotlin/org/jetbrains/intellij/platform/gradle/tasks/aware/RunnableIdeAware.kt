// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.process.JavaForkOptions

/**
 * The interface which uses a set of various interfaces required for running a guest IDE.
 *
 * @see CoroutinesJavaAgentAware
 * @see PluginAware
 * @see RuntimeAware
 * @see SandboxAware
 * @see SplitModeAware
 * @see JavaForkOptions
 */
interface RunnableIdeAware : CoroutinesJavaAgentAware, PluginAware, RuntimeAware, SandboxAware, SplitModeAware, JavaForkOptions
