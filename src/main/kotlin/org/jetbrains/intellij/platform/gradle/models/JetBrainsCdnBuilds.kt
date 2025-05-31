// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable

@Serializable
data class JetBrainsCdnBuilds(
    val code: String,
    val releases: List<Release> = mutableListOf(),
) {

    @Serializable
    data class Release(
        val type: String,
        val version: String,
        val build: String,
    )
}
