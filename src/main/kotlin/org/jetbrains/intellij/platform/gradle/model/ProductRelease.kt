// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.model

import org.jetbrains.intellij.platform.gradle.utils.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.Version

data class ProductRelease(

    val name: String,
    val type: IntelliJPlatformType,
    val channel: Channel,
    val build: Version,
    val version: Version,
    val id: String,
) {

    enum class Channel {
        EAP, MILESTONE, BETA, RELEASE, CANARY, PATCH, RC, PREVIEW;
    }
}