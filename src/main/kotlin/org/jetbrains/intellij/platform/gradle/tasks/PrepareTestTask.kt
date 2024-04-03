// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware

@CacheableTask
abstract class PrepareTestTask : DefaultTask(), TestableAware
