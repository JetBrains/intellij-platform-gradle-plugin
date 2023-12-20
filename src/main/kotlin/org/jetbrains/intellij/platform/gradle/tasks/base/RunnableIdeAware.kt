// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.base

import org.gradle.process.JavaForkOptions

interface RunnableIdeAware : CoroutinesJavaAgentAware, JetBrainsRuntimeAware, SandboxAware, JavaForkOptions
