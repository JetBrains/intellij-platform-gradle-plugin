// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

internal abstract class InstrumentCodeTaskLockService : BuildService<BuildServiceParameters.None>
