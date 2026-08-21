// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants

internal val nativeVariantsIntelliJPlatformVersion =
    Constants.Constraints.MINIMAL_NATIVE_VARIANTS_VERSION.toString()

internal val nativeVariantsSinceBuild =
    Constants.Constraints.MINIMAL_NATIVE_VARIANTS_BUILD_NUMBER.major.toString()
